package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.from
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.common.log.logAndReturnFailure
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntry
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.StashFoodRef
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import com.maksimowiczm.foodyou.stash.domain.repository.StashOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import kotlinx.coroutines.flow.first

sealed interface RemoveStashItemError {
    data class ItemNotFound(val itemId: StashEntryId) : RemoveStashItemError
}

class RemoveStashItemUseCase(
    private val stashRepository: StashRepository,
    private val stashOwnerProvider: StashOwnerProvider,
    private val dateProvider: DateProvider,
    private val transactionProvider: TransactionProvider,
    private val logger: Logger,
) {
    suspend fun remove(
        itemId: StashEntryId,
        action: ManualStashAction,
    ): Result<StashEntry, RemoveStashItemError> {
        val ownerId = stashOwnerProvider.current()
        return transactionProvider.withTransaction {
            val ownedStashIds = stashRepository.observeStashes(ownerId).first().map { it.id }.toSet()
            val item = stashRepository.getItem(itemId)
            if (item == null || item.stashId !in ownedStashIds) {
                return@withTransaction logger.logAndReturnFailure(
                    tag = TAG,
                    error = RemoveStashItemError.ItemNotFound(itemId),
                    message = { "Stash item $itemId not found for owner $ownerId." },
                )
            }

            val removedItem =
                item.copy(
                    measurement = StashMeasurement(Measurement.from(item.measurement.type, 0.0))
                )
            if (removedItem.canDeleteWhenEmpty()) {
                stashRepository.deleteItem(item.id)
            } else {
                stashRepository.updateItem(removedItem)
            }
            if (item.measurement.measurement.rawValue > 0.0) {
                stashRepository.insertMovement(
                    StashMovement.new(
                        stashId = item.stashId,
                        itemId = item.id,
                        operation = StashMovementOperation.ManualAdjust,
                        measurementChange = item.measurement.negate(),
                        linkedDiaryEntryId = null,
                        createdAt = dateProvider.now(),
                        note = action.toMovementNote(),
                    )
                )
            }
            Ok(removedItem)
        }
    }

    private fun StashEntry.canDeleteWhenEmpty(): Boolean =
        when (foodRef) {
            is StashFoodRef.Product -> false
            is StashFoodRef.Recipe -> true
        }

    private companion object {
        const val TAG = "RemoveStashItemUseCase"
    }
}
