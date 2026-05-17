package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.common.log.logAndReturnFailure
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFood
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodProduct
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodRecipe
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntry
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId
import com.maksimowiczm.foodyou.fooddiary.domain.repository.FoodDiaryEntryRepository
import com.maksimowiczm.foodyou.stash.domain.entity.AnonymousDishSnapshot
import com.maksimowiczm.foodyou.stash.domain.entity.LinkedDiaryEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.RawProductSnapshot
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinition
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashItem
import com.maksimowiczm.foodyou.stash.domain.entity.StashItemId
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import com.maksimowiczm.foodyou.stash.domain.entity.StashName
import com.maksimowiczm.foodyou.stash.domain.entity.StashSnapshot
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
    val itemId: StashItemId,
)

class ReturnPartialMealToStashUseCase(
    private val entryRepository: FoodDiaryEntryRepository,
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
            val snapshot =
                buildReturnedSnapshot(
                    entry = entry,
                    measurementToReturn = measurementToReturn,
                )
            val returnedItem =
                StashItem.new(
                    stashId = targetStash.id,
                    snapshot = snapshot,
                    measurement = measurementToReturn,
                    createdAt = now,
                )
            val itemId = stashRepository.insertItem(returnedItem)
            stashRepository.insertMovement(
                StashMovement.new(
                    stashId = targetStash.id,
                    itemId = itemId,
                    operation = StashMovementOperation.ReturnToStash,
                    measurementChange = measurementToReturn,
                    linkedDiaryEntryId = LinkedDiaryEntryId(entry.id.value),
                    createdAt = now,
                )
            )
            entryRepository.update(
                entry.copy(
                    measurement = entry.measurement * remainingRatio,
                    mealId = mealId ?: entry.mealId,
                    date = date ?: entry.date,
                    updatedAt = now,
                )
            )

            Ok(ReturnPartialMealToStashResult(stashId = targetStash.id, itemId = itemId))
        }
    }

    private suspend fun buildReturnedSnapshot(
        entry: FoodDiaryEntry,
        measurementToReturn: StashMeasurement,
    ): StashSnapshot {
        // Prefer the original linked stash snapshot when history still points to it so leftovers keep
        // the same immutable product/recipe metadata as the consumed stock.
        val linkedSnapshot = findLinkedSnapshot(entry.id)
        return when (linkedSnapshot) {
            is RawProductSnapshot ->
                linkedSnapshot.copy(
                    packageWeight = measurementToReturn.measurement.rawValue,
                    servingWeight = linkedSnapshot.servingWeight?.coerceAtMost(measurementToReturn.measurement.rawValue),
                )

            is AnonymousDishSnapshot ->
                linkedSnapshot.copy(
                    totalWeight = measurementToReturn.measurement.rawValue,
                    totalAmount = measurementToReturn.measurement,
                )

            null -> entry.food.toReturnedSnapshot(measurementToReturn)
        }
    }

    private suspend fun findLinkedSnapshot(entryId: FoodDiaryEntryId): StashSnapshot? {
        val linkedMovements =
            stashRepository
                .getLinkedDiaryEntryMovements(LinkedDiaryEntryId(entryId.value))
                .sortedWith(
                    compareBy<StashMovement> { movement ->
                        if (movement.measurementChange.measurement.rawValue < 0.0) {
                            0
                        } else {
                            1
                        }
                    }.thenBy { it.id.value }
                )

        linkedMovements.forEach { movement ->
            val snapshot = stashRepository.getItem(movement.itemId)?.snapshot
            if (snapshot != null) {
                return snapshot
            }
        }

        return null
    }

    private fun FoodDiaryEntry.toStashMeasurement(): StashMeasurement =
        if (food.isLiquid) {
            StashMeasurement.milliliters(weight)
        } else {
            StashMeasurement.grams(weight)
        }

    private fun DiaryFood.toReturnedSnapshot(measurementToReturn: StashMeasurement): StashSnapshot =
        when (this) {
            is DiaryFoodProduct ->
                RawProductSnapshot(
                    productId = null,
                    name = name,
                    brand = null,
                    barcode = null,
                    note = note,
                    isLiquid = isLiquid,
                    packageWeight = measurementToReturn.measurement.rawValue,
                    servingWeight = servingWeight?.coerceAtMost(measurementToReturn.measurement.rawValue),
                    source = source,
                    nutritionFacts = nutritionFacts,
                )

            is DiaryFoodRecipe ->
                AnonymousDishSnapshot(
                    name = name,
                    nutritionFacts = nutritionFacts,
                    note = note,
                    isLiquid = isLiquid,
                    totalWeight = measurementToReturn.measurement.rawValue,
                    totalAmount = measurementToReturn.measurement,
                )
        }

    private companion object {
        const val TAG = "ReturnPartialMealToStashUseCase"
        const val DEFAULT_STASH_NAME = "Stash"
        const val EPSILON = 0.000001
    }
}
