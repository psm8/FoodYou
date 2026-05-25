package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.from
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.common.log.logAndReturnFailure
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodProduct
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntry
import com.maksimowiczm.foodyou.stash.domain.entity.LinkedDiaryEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntry
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.StashFoodRef
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import kotlin.math.abs

sealed interface RestoreLinkedDiaryEntryStashError {
    data class LinkedItemNotFound(val itemId: StashEntryId) : RestoreLinkedDiaryEntryStashError

    data class InsufficientQuantityToReverse(
        val itemId: StashEntryId,
        val requested: StashMeasurement,
        val available: StashMeasurement,
    ) : RestoreLinkedDiaryEntryStashError
}

class RestoreLinkedDiaryEntryStashUseCase(
    private val stashRepository: StashRepository,
    private val productRepository: ProductRepository,
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
                    recreateItem(
                        entry = entry,
                        itemId = movement.itemId,
                        stashId = movement.stashId,
                        measurement = reversalMeasurement,
                        createdAt = now,
                    ) ?: return logger.logAndReturnFailure(
                        tag = TAG,
                        error = RestoreLinkedDiaryEntryStashError.LinkedItemNotFound(movement.itemId),
                        message = { "Cannot recreate stash item ${movement.itemId}; linked food reference is unavailable." },
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

            val targetItemId =
                if (item == null) {
                    stashRepository.insertItem(targetItem)
                } else {
                    stashRepository.updateItem(targetItem)
                    targetItem.id
                }
            stashRepository.insertMovement(
                StashMovement.new(
                    stashId = targetItem.stashId,
                    itemId = targetItemId,
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
                        ?: recreateItem(
                            entry = entry,
                            itemId = itemId,
                            stashId = stashId,
                            measurement = adjustmentMeasurement,
                            createdAt = now,
                        ) ?: return logger.logAndReturnFailure(
                            tag = TAG,
                            error = RestoreLinkedDiaryEntryStashError.LinkedItemNotFound(itemId),
                            message = { "Cannot recreate stash item $itemId during rebalance; linked food reference is unavailable." },
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

            val targetItemId =
                if (currentItem == null) {
                    stashRepository.insertItem(targetItem)
                } else {
                    stashRepository.updateItem(targetItem)
                    targetItem.id
                }
            stashRepository.insertMovement(
                StashMovement.new(
                    stashId = stashId,
                    itemId = targetItemId,
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

    private suspend fun recreateItem(
        entry: FoodDiaryEntry,
        itemId: StashEntryId,
        stashId: StashDefinitionId,
        measurement: StashMeasurement,
        createdAt: kotlinx.datetime.LocalDateTime,
    ): StashEntry? {
        val product = entry.food as? DiaryFoodProduct ?: return null
        val productId = product.ensureProductId(productRepository)
        return StashEntry(
            id = itemId,
            stashId = stashId,
            foodRef = StashFoodRef.Product(productId),
            measurement = measurement,
            createdAt = createdAt,
        )
    }

    private companion object {
        const val TAG = "RestoreLinkedDiaryEntryStashUseCase"
        const val EPSILON = 0.000001
    }
}
