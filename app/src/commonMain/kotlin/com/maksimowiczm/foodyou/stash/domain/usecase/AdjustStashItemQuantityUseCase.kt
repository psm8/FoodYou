package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.common.log.logAndReturnFailure
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.stash.domain.entity.AnonymousDishSnapshot
import com.maksimowiczm.foodyou.stash.domain.entity.RawProductSnapshot
import com.maksimowiczm.foodyou.stash.domain.entity.StashItem
import com.maksimowiczm.foodyou.stash.domain.entity.StashItemId
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantityUnit
import com.maksimowiczm.foodyou.stash.domain.repository.StashOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import kotlin.math.abs
import kotlinx.coroutines.flow.first

sealed interface AdjustStashItemQuantityError {
    data class ItemNotFound(val itemId: StashItemId) : AdjustStashItemQuantityError

    data class QuantityUnitMismatch(
        val expected: StashQuantityUnit,
        val actual: StashQuantityUnit,
    ) : AdjustStashItemQuantityError

    data class QuantityBelowZero(val quantity: StashQuantity) : AdjustStashItemQuantityError
}

class AdjustStashItemQuantityUseCase(
    private val stashRepository: StashRepository,
    private val stashOwnerProvider: StashOwnerProvider,
    private val dateProvider: DateProvider,
    private val transactionProvider: TransactionProvider,
    private val logger: Logger,
) {
    suspend fun adjust(
        itemId: StashItemId,
        adjustment: StashQuantityAdjustment,
        action: ManualStashAction,
    ): Result<StashItem, AdjustStashItemQuantityError> {
        val ownerId = stashOwnerProvider.current()
        return transactionProvider.withTransaction {
            val ownedStashIds = stashRepository.observeStashes(ownerId).first().map { it.id }.toSet()
            val item = stashRepository.getItem(itemId)
            if (item == null || item.stashId !in ownedStashIds) {
                return@withTransaction logger.logAndReturnFailure(
                    tag = TAG,
                    error = AdjustStashItemQuantityError.ItemNotFound(itemId),
                    message = { "Stash item $itemId not found for owner $ownerId." },
                )
            }

            if (item.quantity.unit != adjustment.quantity.unit) {
                return@withTransaction logger.logAndReturnFailure(
                    tag = TAG,
                    error =
                        AdjustStashItemQuantityError.QuantityUnitMismatch(
                            expected = item.quantity.unit,
                            actual = adjustment.quantity.unit,
                        ),
                    message = { "Cannot adjust stash item $itemId with mismatched units." },
                )
            }

            val targetQuantity =
                when (adjustment) {
                    is StashQuantityAdjustment.ChangeBy -> item.quantity + adjustment.quantity
                    is StashQuantityAdjustment.SetTo -> adjustment.quantity
                }
            if (targetQuantity.amount < -EPSILON) {
                return@withTransaction logger.logAndReturnFailure(
                    tag = TAG,
                    error = AdjustStashItemQuantityError.QuantityBelowZero(targetQuantity),
                    message = { "Cannot adjust stash item $itemId below zero." },
                )
            }

            val normalizedTarget = targetQuantity.normalize()
            if (normalizedTarget.isSameAmountAs(item.quantity)) {
                return@withTransaction Ok(item)
            }

            val updatedItem = item.copy(quantity = normalizedTarget)
            if (updatedItem.quantity.amount <= EPSILON && updatedItem.canDeleteWhenEmpty()) {
                stashRepository.deleteItem(updatedItem.id)
            } else {
                stashRepository.updateItem(updatedItem)
            }
            stashRepository.insertMovement(
                StashMovement.new(
                    stashId = item.stashId,
                    itemId = item.id,
                    operation = StashMovementOperation.ManualAdjust,
                    quantityChange = normalizedTarget - item.quantity,
                    linkedDiaryEntryId = null,
                    createdAt = dateProvider.now(),
                    note = action.toMovementNote(),
                )
            )
            Ok(updatedItem)
        }
    }

    private fun StashQuantity.normalize(): StashQuantity = if (amount < EPSILON) copy(amount = 0.0) else this

    private fun StashQuantity.isSameAmountAs(other: StashQuantity): Boolean =
        unit == other.unit && abs(amount - other.amount) <= EPSILON

    private fun StashItem.canDeleteWhenEmpty(): Boolean =
        when (val snapshot = snapshot) {
            is RawProductSnapshot -> snapshot.productId == null
            is AnonymousDishSnapshot -> true
        }

    private companion object {
        const val TAG = "AdjustStashItemQuantityUseCase"
        const val EPSILON = 1e-6
    }
}
