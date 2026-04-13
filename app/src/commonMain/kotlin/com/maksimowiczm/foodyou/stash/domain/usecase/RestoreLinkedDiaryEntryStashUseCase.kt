package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
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
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
import com.maksimowiczm.foodyou.stash.domain.entity.StashSnapshot
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import kotlin.math.abs

sealed interface RestoreLinkedDiaryEntryStashError {
    data class LinkedItemNotFound(val itemId: StashItemId) : RestoreLinkedDiaryEntryStashError

    data class InsufficientQuantityToReverse(
        val itemId: StashItemId,
        val requested: StashQuantity,
        val available: StashQuantity,
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
            val reversalQuantity = movement.quantityChange.negate().scale(restoreRatio).normalize()
            if (abs(reversalQuantity.amount) <= EPSILON) {
                return@forEach
            }

            val item = stashRepository.getItem(movement.itemId)
            if (item == null && reversalQuantity.amount <= 0.0) {
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
                        quantity = reversalQuantity,
                        createdAt = movement.createdAt,
                    )
                } else if (reversalQuantity.amount > 0.0) {
                    item.copy(quantity = (item.quantity + reversalQuantity).normalize())
                } else {
                    val quantityToRemove = reversalQuantity.negate()
                    if (item.quantity.amount + EPSILON < quantityToRemove.amount) {
                        return logger.logAndReturnFailure(
                            tag = TAG,
                            error =
                                RestoreLinkedDiaryEntryStashError.InsufficientQuantityToReverse(
                                    itemId = item.id,
                                    requested = quantityToRemove,
                                    available = item.quantity,
                                ),
                            message = {
                                "Cannot reverse linked stash movement ${movement.id}; item ${item.id} only has ${item.quantity.amount}."
                            },
                        )
                    }

                    item.copy(quantity = (item.quantity - quantityToRemove).normalize())
                }

            if (targetItem.quantity.amount <= EPSILON && targetItem.canDeleteWhenEmpty()) {
                stashRepository.deleteItem(targetItem.id)
            } else {
                stashRepository.updateItem(targetItem)
            }
            stashRepository.insertMovement(
                StashMovement.new(
                    stashId = targetItem.stashId,
                    itemId = targetItem.id,
                    operation = reversalOperation,
                    quantityChange = reversalQuantity,
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
        val linkedMovementsByItem =
            stashRepository
                .getLinkedDiaryEntryMovements(linkedDiaryEntryId)
                .groupBy(StashMovement::itemId)
                .values
                .sortedBy { itemMovements -> itemMovements.minOf { movement -> movement.id.value } }

        val now = dateProvider.now()
        linkedMovementsByItem.forEach { itemMovements ->
            val currentNetQuantity = itemMovements.netQuantityChange()
            val desiredNetQuantity = currentNetQuantity.scale(updatedWeightRatio)
            val adjustmentQuantity = (desiredNetQuantity - currentNetQuantity).normalize()

            if (abs(adjustmentQuantity.amount) <= EPSILON) {
                return@forEach
            }

            val itemId = itemMovements.first().itemId
            val stashId = itemMovements.first().stashId
            val currentItem = stashRepository.getItem(itemId)

            val targetItem =
                if (adjustmentQuantity.amount > 0.0) {
                    currentItem?.copy(quantity = (currentItem.quantity + adjustmentQuantity).normalize())
                        ?: createLinkedItem(
                            entry = entry,
                            itemId = itemId,
                            stashId = stashId,
                            quantity = adjustmentQuantity,
                            createdAt = itemMovements.first().createdAt,
                        )
                } else {
                    val quantityToRemove = adjustmentQuantity.negate()
                    val item = currentItem ?: return logger.logAndReturnFailure(
                        tag = TAG,
                        error = RestoreLinkedDiaryEntryStashError.LinkedItemNotFound(itemId),
                        message = { "Cannot rebalance linked stash movements for item $itemId; item not found." },
                    )
                    if (item.quantity.amount + EPSILON < quantityToRemove.amount) {
                        return logger.logAndReturnFailure(
                            tag = TAG,
                            error =
                                RestoreLinkedDiaryEntryStashError.InsufficientQuantityToReverse(
                                    itemId = item.id,
                                    requested = quantityToRemove,
                                    available = item.quantity,
                                ),
                            message = {
                                "Cannot rebalance linked stash movements for item ${item.id}; only ${item.quantity.amount} is available."
                            },
                        )
                    }

                    item.copy(quantity = (item.quantity - quantityToRemove).normalize())
                }

            if (targetItem.quantity.amount <= EPSILON && targetItem.canDeleteWhenEmpty()) {
                stashRepository.deleteItem(targetItem.id)
            } else {
                stashRepository.updateItem(targetItem)
            }
            stashRepository.insertMovement(
                StashMovement.new(
                    stashId = stashId,
                    itemId = targetItem.id,
                    operation = StashMovementOperation.AutoReversalOnEdit,
                    quantityChange = adjustmentQuantity,
                    linkedDiaryEntryId = linkedDiaryEntryId,
                    createdAt = now,
                )
            )
        }

        return Ok(Unit)
    }

    private fun List<StashMovement>.netQuantityChange(): StashQuantity {
        val zeroQuantity = first().quantityChange.copy(amount = 0.0)
        return fold(zeroQuantity) { total, movement -> total + movement.quantityChange }
    }

    private fun StashQuantity.scale(factor: Double): StashQuantity = copy(amount = amount * factor)

    private fun StashQuantity.normalize(): StashQuantity =
        if (abs(amount) <= EPSILON) {
            copy(amount = 0.0)
        } else {
            this
        }

    private fun createLinkedItem(
        entry: FoodDiaryEntry,
        itemId: StashItemId,
        stashId: StashDefinitionId,
        quantity: StashQuantity,
        createdAt: kotlinx.datetime.LocalDateTime,
    ): StashItem =
        StashItem(
            id = itemId,
            stashId = stashId,
            snapshot = entry.food.toReturnedSnapshot(quantity),
            quantity = quantity.normalize(),
            createdAt = createdAt,
        )

    private fun DiaryFood.toReturnedSnapshot(quantity: StashQuantity): StashSnapshot =
        when (this) {
            is DiaryFoodProduct ->
                RawProductSnapshot(
                    productId = null,
                    name = name,
                    brand = null,
                    barcode = null,
                    note = note,
                    isLiquid = isLiquid,
                    packageWeight = quantity.amount,
                    servingWeight = servingWeight?.coerceAtMost(quantity.amount),
                    source = source,
                    nutritionFacts = nutritionFacts,
                )

            is DiaryFoodRecipe ->
                AnonymousDishSnapshot(
                    name = name,
                    nutritionFacts = nutritionFacts,
                    note = note,
                    isLiquid = isLiquid,
                    totalWeight = quantity.amount,
                    totalAmount = quantity,
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
