package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.common.domain.measurement.from
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.common.log.logAndReturnFailure
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodProduct
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntry
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId
import com.maksimowiczm.foodyou.fooddiary.domain.repository.FoodDiaryEntryRepository
import com.maksimowiczm.foodyou.stash.domain.entity.LinkedDiaryEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinition
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntry
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.StashFoodRef
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import com.maksimowiczm.foodyou.stash.domain.entity.StashName
import com.maksimowiczm.foodyou.stash.domain.repository.StashOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.LocalDate

sealed interface ReturnPartialMealToStashError {
    data object EntryNotFound : ReturnPartialMealToStashError

    data class StashNotFound(val id: StashDefinitionId) : ReturnPartialMealToStashError

    data object StashSelectionRequired : ReturnPartialMealToStashError

    data object NonPositiveQuantity : ReturnPartialMealToStashError

    data object InvalidMeasurement : ReturnPartialMealToStashError

    data object QuantityExceedsEntry : ReturnPartialMealToStashError

    data object NoConsumedAmountRemaining : ReturnPartialMealToStashError
}

data class ReturnPartialMealToStashResult(
    val stashId: StashDefinitionId,
    val itemId: StashEntryId,
)

class ReturnPartialMealToStashUseCase(
    private val entryRepository: FoodDiaryEntryRepository,
    private val productRepository: ProductRepository,
    private val stashRepository: StashRepository,
    private val stashOwnerProvider: StashOwnerProvider,
    private val transactionProvider: TransactionProvider,
    private val dateProvider: DateProvider,
    private val logger: Logger,
) {
    suspend fun returnToStash(
        entryId: FoodDiaryEntryId,
        measurementToReturn: StashMeasurement,
        mealId: Long? = null,
        date: LocalDate? = null,
        stashId: StashDefinitionId? = null,
    ): Result<ReturnPartialMealToStashResult, ReturnPartialMealToStashError> {
        if (measurementToReturn.measurement.rawValue <= 0.0) {
            return logger.logAndReturnFailure(
                tag = TAG,
                error = ReturnPartialMealToStashError.NonPositiveQuantity,
                message = { "Returned stash measurement must be greater than 0." },
            )
        }

        return transactionProvider.withTransaction {
            val entry = entryRepository.observe(entryId).firstOrNull()
            if (entry == null) {
                return@withTransaction logger.logAndReturnFailure(
                    tag = TAG,
                    error = ReturnPartialMealToStashError.EntryNotFound,
                    message = { "Diary entry with id $entryId not found." },
                )
            }

            val entryMeasurement = entry.toStashMeasurement()
            if (measurementToReturn.type != entryMeasurement.type) {
                return@withTransaction logger.logAndReturnFailure(
                    tag = TAG,
                    error = ReturnPartialMealToStashError.InvalidMeasurement,
                    message = { "Diary entry $entryId does not support stash measurement type ${measurementToReturn.type}." },
                )
            }

            if (entryMeasurement.measurement.rawValue + EPSILON < measurementToReturn.measurement.rawValue) {
                return@withTransaction logger.logAndReturnFailure(
                    tag = TAG,
                    error = ReturnPartialMealToStashError.QuantityExceedsEntry,
                    message = { "Cannot return ${measurementToReturn.measurement.rawValue}; diary entry only weighs ${entryMeasurement.measurement.rawValue}." },
                )
            }

            // Scale the existing measurement by ratio instead of subtracting raw grams so
            // serving/package-based diary entries keep the same semantic unit after the return.
            val remainingRatio = (entryMeasurement.measurement.rawValue - measurementToReturn.measurement.rawValue) / entryMeasurement.measurement.rawValue
            if (remainingRatio <= EPSILON) {
                return@withTransaction logger.logAndReturnFailure(
                    tag = TAG,
                    error = ReturnPartialMealToStashError.NoConsumedAmountRemaining,
                    message = { "Diary entry $entryId must keep a positive consumed amount after returning leftovers." },
                )
            }

            val now = dateProvider.now()
            val ownerId = stashOwnerProvider.current()
            val stashes = stashRepository.observeStashes(ownerId).first()
            val targetStash =
                when {
                    stashId != null ->
                        stashes.firstOrNull { it.id == stashId } ?: return@withTransaction logger.logAndReturnFailure(
                            tag = TAG,
                            error = ReturnPartialMealToStashError.StashNotFound(stashId),
                            message = { "Stash with id $stashId not found." },
                        )

                    stashes.isEmpty() -> {
                        val defaultStash =
                            StashDefinition.new(
                                ownerId = ownerId,
                                name = StashName.from(DEFAULT_STASH_NAME),
                                createdAt = now,
                                ordering = 0,
                            )
                        val createdStashId = stashRepository.insertStash(defaultStash)
                        defaultStash.copy(id = createdStashId)
                    }

                    stashes.size == 1 -> stashes.single()

                    else ->
                        return@withTransaction logger.logAndReturnFailure(
                            tag = TAG,
                            error = ReturnPartialMealToStashError.StashSelectionRequired,
                            message = { "A stash must be selected when more than one stash exists." },
                        )
                }
            val source = resolveReturnSource(entry, measurementToReturn, targetStash.id)
                ?: return@withTransaction logger.logAndReturnFailure(
                    tag = TAG,
                    error = ReturnPartialMealToStashError.InvalidMeasurement,
                    message = { "Diary entry $entryId does not provide enough context to return $measurementToReturn." },
                )

            val itemId =
                if (source.existingItem == null) {
                    stashRepository.insertItem(
                        StashEntry.new(
                            stashId = targetStash.id,
                            foodRef = source.foodRef,
                            measurement = source.returnedMeasurement,
                            createdAt = now,
                        )
                    )
                } else {
                    stashRepository.updateItem(
                        source.existingItem.copy(
                            measurement = (source.existingItem.measurement + source.returnedMeasurement).normalize(),
                        )
                    )
                    source.existingItem.id
                }

            stashRepository.insertMovement(
                StashMovement.new(
                    stashId = targetStash.id,
                    itemId = itemId,
                    operation = StashMovementOperation.ReturnToStash,
                    measurementChange = source.returnedMeasurement,
                    linkedDiaryEntryId = LinkedDiaryEntryId(entry.id.value),
                    createdAt = now,
                )
            )

            entryRepository.update(
                entry.copy(
                    measurement = (entry.measurement * remainingRatio),
                    mealId = mealId ?: entry.mealId,
                    date = date ?: entry.date,
                    updatedAt = now,
                )
            )

            Ok(ReturnPartialMealToStashResult(stashId = targetStash.id, itemId = itemId))
        }
    }

    private suspend fun resolveReturnSource(
        entry: FoodDiaryEntry,
        measurementToReturn: StashMeasurement,
        targetStashId: StashDefinitionId,
    ): ReturnSource? {
        val linkedMovement =
            stashRepository.getLinkedDiaryEntryMovements(LinkedDiaryEntryId(entry.id.value)).firstOrNull()
        val linkedItem = linkedMovement?.let { stashRepository.getItem(it.itemId) }
        if (linkedItem != null) {
            val returnedMeasurement = linkedItem.foodRef.toReturnedMeasurement(linkedItem.measurement.type, measurementToReturn) ?: return null
            val existingItem =
                if (linkedItem.stashId == targetStashId) {
                    linkedItem
                } else {
                    null
                }
            return ReturnSource(existingItem = existingItem, foodRef = linkedItem.foodRef, returnedMeasurement = returnedMeasurement)
        }

        val diaryFood = entry.food as? DiaryFoodProduct ?: return null
        val productId = diaryFood.ensureProductId(productRepository)
        return ReturnSource(
            existingItem = findMergeCandidate(targetStashId, StashFoodRef.Product(productId), measurementToReturn.type),
            foodRef = StashFoodRef.Product(productId),
            returnedMeasurement = measurementToReturn,
        )
    }

    private suspend fun findMergeCandidate(
        stashId: StashDefinitionId,
        foodRef: StashFoodRef,
        measurementType: MeasurementType,
    ): StashEntry? =
        stashRepository.observeStashContents(stashId).first().firstOrNull {
            it.foodRef == foodRef && it.measurement.type == measurementType
        }

    private fun StashFoodRef.toReturnedMeasurement(
        stashMeasurementType: MeasurementType,
        measurementToReturn: StashMeasurement,
    ): StashMeasurement? =
        when (this) {
            is StashFoodRef.Product ->
                if (measurementToReturn.type == stashMeasurementType) {
                    measurementToReturn
                } else {
                    null
                }

            is StashFoodRef.Recipe -> recipeReturnedMeasurement(stashMeasurementType, measurementToReturn)
        }

    private fun StashFoodRef.Recipe.recipeReturnedMeasurement(
        stashMeasurementType: MeasurementType,
        measurementToReturn: StashMeasurement,
    ): StashMeasurement? {
        if (measurementToReturn.type == stashMeasurementType) {
            return measurementToReturn
        }

        val requestedWeightType =
            when (measurementToReturn.type) {
                MeasurementType.Gram,
                MeasurementType.Milliliter,
                MeasurementType.Ounce,
                MeasurementType.FluidOunce,
                -> true

                else -> false
            }
        val stashPortionType =
            when (stashMeasurementType) {
                MeasurementType.Serving,
                MeasurementType.Package,
                -> true

                else -> false
            }
        if (!requestedWeightType || !stashPortionType || totalWeight <= EPSILON) {
            return null
        }

        val rawValueChange = totalAmount.rawValue * (measurementToReturn.measurement.rawValue / totalWeight)
        return StashMeasurement(Measurement.from(stashMeasurementType, rawValueChange))
    }

    private fun FoodDiaryEntry.toStashMeasurement(): StashMeasurement =
        if (food.isLiquid) {
            StashMeasurement.milliliters(weight)
        } else {
            StashMeasurement.grams(weight)
        }

    private data class ReturnSource(
        val existingItem: StashEntry?,
        val foodRef: StashFoodRef,
        val returnedMeasurement: StashMeasurement,
    )

    private companion object {
        const val TAG = "ReturnPartialMealToStashUseCase"
        const val DEFAULT_STASH_NAME = "Stash"
        const val EPSILON = 0.000001
    }
}
