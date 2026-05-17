package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.from
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.common.log.logAndReturnFailure
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFood
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodProduct
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodRecipe
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntry
import com.maksimowiczm.foodyou.stash.domain.entity.AnonymousDishSnapshot
import com.maksimowiczm.foodyou.stash.domain.entity.LinkedDiaryEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.RawProductSnapshot
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashItem
import com.maksimowiczm.foodyou.stash.domain.entity.StashItemId
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import com.maksimowiczm.foodyou.stash.domain.entity.StashSnapshot
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import kotlin.math.abs

sealed interface RestoreLinkedDiaryEntryStashError {
    data class LinkedItemNotFound(val itemId: StashItemId) : RestoreLinkedDiaryEntryStashError

    data class InsufficientQuantityToReverse(
        val itemId: StashItemId,
        val requested: StashMeasurement,
        val available: StashMeasurement,
    ) : RestoreLinkedDiaryEntryStashError
}

class RestoreLinkedDiaryEntryStashUseCase(
    private val stashRepository: StashRepository,
    private val dateProvider: DateProvider,
    private val logger: Logger,
) {
    suspend fun rebalanceEditedEntry(
        entry: FoodDiaryEntry,
        updatedMeasurement: Measurement,
    ): Result<Unit, RestoreLinkedDiaryEntryStashError> {
        val previousWeight = entry.weight
        if (previousWeight <= EPSILON) {
            return Ok(Unit)
        }

        val updatedWeight = entry.food.weight(updatedMeasurement)
        if (abs(updatedWeight - previousWeight) <= EPSILON) {
            return Ok(Unit)
        }

        return applyEditRebalance(
            entry = entry,
            linkedDiaryEntryId = LinkedDiaryEntryId(entry.id.value),
            updatedWeightRatio = (updatedWeight / previousWeight).coerceAtLeast(0.0),
        )
    }

    suspend fun restoreAll(entry: FoodDiaryEntry): Result<Unit, RestoreLinkedDiaryEntryStashError> =
        reverseMovements(
            entry = entry,
            linkedDiaryEntryId = LinkedDiaryEntryId(entry.id.value),
            movementsFilter = { true },
            restoreRatio = 1.0,
            reversalOperation = StashMovementOperation.AutoReversalOnDelete,
        )

    private suspend fun reverseMovements(
        entry: FoodDiaryEntry,
        linkedDiaryEntryId: LinkedDiaryEntryId,
        movementsFilter: (StashMovement) -> Boolean,
        restoreRatio: Double,
        reversalOperation: StashMovementOperation,
    ): Result<Unit, RestoreLinkedDiaryEntryStashError> {
        val linkedMovements =
            stashRepository
                .getLinkedDiaryEntryMovements(linkedDiaryEntryId)
                .filter(movementsFilter)

        val now = dateProvider.now()
        linkedMovements.forEach { movement ->
            val reversalMeasurement = movement.measurementChange.negate().scale(restoreRatio).normalize()
            if (abs(reversalMeasurement.measurement.rawValue) <= EPSILON) {
                return@forEach
            }

            val item = stashRepository.getItem(movement.itemId)
            if (item == null && reversalMeasurement.measurement.rawValue <= 0.0) {
                return logger.logAndReturnFailure(
                    tag = TAG,
                    error = RestoreLinkedDiaryEntryStashError.LinkedItemNotFound(movement.itemId),
                    message = { "Cannot reverse linked stash movement ${movement.id}; item ${movement.itemId} not found." },
                )
            }

            val targetItem =
                if (item == null) {
                    createLinkedItem(
                        entry = entry,
                        itemId = movement.itemId,
                        stashId = movement.stashId,
                        measurement = reversalMeasurement,
                        createdAt = movement.createdAt,
                    )
                } else if (reversalMeasurement.measurement.rawValue > 0.0) {
                    item.copy(measurement = (item.measurement + reversalMeasurement).normalize())
                } else {
                    val measurementToRemove = reversalMeasurement.negate()
                    if (item.measurement.measurement.rawValue + EPSILON < measurementToRemove.measurement.rawValue) {
                        return logger.logAndReturnFailure(
                            tag = TAG,
                            error =
                                RestoreLinkedDiaryEntryStashError.InsufficientQuantityToReverse(
                                    itemId = item.id,
                                    requested = measurementToRemove,
                                    available = item.measurement,
                                ),
                            message = {
                                "Cannot reverse linked stash movement ${movement.id}; item ${item.id} only has ${item.measurement.measurement.rawValue}."
                            },
                        )
                    }

                    item.copy(measurement = (item.measurement - measurementToRemove).normalize())
                }

            if (targetItem.measurement.measurement.rawValue <= EPSILON && targetItem.canDeleteWhenEmpty()) {
                stashRepository.deleteItem(targetItem.id)
            } else {
                stashRepository.updateItem(targetItem)
            }
            stashRepository.insertMovement(
                StashMovement.new(
                    stashId = targetItem.stashId,
                    itemId = targetItem.id,
                    operation = reversalOperation,
                    measurementChange = reversalMeasurement,
                    linkedDiaryEntryId = linkedDiaryEntryId,
                    createdAt = now,
                )
            )
        }

        return Ok(Unit)
    }

    private suspend fun applyEditRebalance(
        entry: FoodDiaryEntry,
        linkedDiaryEntryId: LinkedDiaryEntryId,
        updatedWeightRatio: Double,
    ): Result<Unit, RestoreLinkedDiaryEntryStashError> {
        // Rebalances scale the net stash effect per item instead of replaying each historical movement,
        // which avoids compounding correction rows when the same diary entry is edited repeatedly.
        val linkedMovementsByItem =
            stashRepository
                .getLinkedDiaryEntryMovements(linkedDiaryEntryId)
                .groupBy(StashMovement::itemId)
                .values
                .sortedBy { itemMovements -> itemMovements.minOf { movement -> movement.id.value } }

        val now = dateProvider.now()
        linkedMovementsByItem.forEach { itemMovements ->
            val currentNetMeasurement = itemMovements.netQuantityChange()
            val desiredNetMeasurement = currentNetMeasurement.scale(updatedWeightRatio)
            val adjustmentMeasurement = (desiredNetMeasurement - currentNetMeasurement).normalize()

            if (abs(adjustmentMeasurement.measurement.rawValue) <= EPSILON) {
                return@forEach
            }

            val itemId = itemMovements.first().itemId
            val stashId = itemMovements.first().stashId
            val currentItem = stashRepository.getItem(itemId)

            val targetItem =
                if (adjustmentMeasurement.measurement.rawValue > 0.0) {
                    currentItem?.copy(measurement = (currentItem.measurement + adjustmentMeasurement).normalize())
                        ?: createLinkedItem(
                            entry = entry,
                            itemId = itemId,
                            stashId = stashId,
                            measurement = adjustmentMeasurement,
                            createdAt = itemMovements.first().createdAt,
                        )
                } else {
                    val measurementToRemove = adjustmentMeasurement.negate()
                    val item = currentItem ?: return logger.logAndReturnFailure(
                        tag = TAG,
                        error = RestoreLinkedDiaryEntryStashError.LinkedItemNotFound(itemId),
                        message = { "Cannot rebalance linked stash movements for item $itemId; item not found." },
                    )
                    if (item.measurement.measurement.rawValue + EPSILON < measurementToRemove.measurement.rawValue) {
                        return logger.logAndReturnFailure(
                            tag = TAG,
                            error =
                                RestoreLinkedDiaryEntryStashError.InsufficientQuantityToReverse(
                                    itemId = item.id,
                                    requested = measurementToRemove,
                                    available = item.measurement,
                                ),
                            message = {
                                "Cannot rebalance linked stash movements for item ${item.id}; only ${item.measurement.measurement.rawValue} is available."
                            },
                        )
                    }

                    item.copy(measurement = (item.measurement - measurementToRemove).normalize())
                }

            if (targetItem.measurement.measurement.rawValue <= EPSILON && targetItem.canDeleteWhenEmpty()) {
                stashRepository.deleteItem(targetItem.id)
            } else {
                stashRepository.updateItem(targetItem)
            }
            stashRepository.insertMovement(
                StashMovement.new(
                    stashId = stashId,
                    itemId = targetItem.id,
                    operation = StashMovementOperation.AutoReversalOnEdit,
                    measurementChange = adjustmentMeasurement,
                    linkedDiaryEntryId = linkedDiaryEntryId,
                    createdAt = now,
                )
            )
        }

        return Ok(Unit)
    }

    private fun List<StashMovement>.netQuantityChange(): StashMeasurement {
        val zeroMeasurement = StashMeasurement(Measurement.from(first().measurementChange.type, 0.0))
        return fold(zeroMeasurement) { total, movement -> total + movement.measurementChange }
    }

    private fun StashMeasurement.scale(factor: Double): StashMeasurement =
        StashMeasurement(Measurement.from(type, measurement.rawValue * factor))

    private fun createLinkedItem(
        entry: FoodDiaryEntry,
        itemId: StashItemId,
        stashId: StashDefinitionId,
        measurement: StashMeasurement,
        createdAt: kotlinx.datetime.LocalDateTime,
    ): StashItem =
        StashItem(
            id = itemId,
            stashId = stashId,
            snapshot = entry.food.toReturnedSnapshot(measurement),
            measurement = measurement.normalize(),
            createdAt = createdAt,
        )

    private fun DiaryFood.toReturnedSnapshot(measurement: StashMeasurement): StashSnapshot =
        when (this) {
            is DiaryFoodProduct ->
                RawProductSnapshot(
                    productId = null,
                    name = name,
                    brand = null,
                    barcode = null,
                    note = note,
                    isLiquid = isLiquid,
                    packageWeight = measurement.measurement.rawValue,
                    servingWeight = servingWeight?.coerceAtMost(measurement.measurement.rawValue),
                    source = source,
                    nutritionFacts = nutritionFacts,
                )

            is DiaryFoodRecipe ->
                AnonymousDishSnapshot(
                    name = name,
                    nutritionFacts = nutritionFacts,
                    note = note,
                    isLiquid = isLiquid,
                    totalWeight = measurement.measurement.rawValue,
                    totalAmount = measurement.measurement,
                )
        }

    private fun StashItem.canDeleteWhenEmpty(): Boolean =
        when (val snapshot = snapshot) {
            is RawProductSnapshot -> snapshot.productId == null
            is AnonymousDishSnapshot -> true
        }

    private companion object {
        const val TAG = "RestoreLinkedDiaryEntryStashUseCase"
        const val EPSILON = 0.000001
    }
}
