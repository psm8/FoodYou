package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.common.log.logAndReturnFailure
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntry
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import com.maksimowiczm.foodyou.stash.domain.repository.StashOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import kotlinx.coroutines.flow.first

sealed interface MoveStashItemError {
    data class ItemNotFound(val itemId: StashEntryId) : MoveStashItemError

    data class TargetStashNotFound(val stashId: StashDefinitionId) : MoveStashItemError

    data class AlreadyInTargetStash(val stashId: StashDefinitionId) : MoveStashItemError
}

class MoveStashItemUseCase(
    private val stashRepository: StashRepository,
    private val stashOwnerProvider: StashOwnerProvider,
    private val dateProvider: DateProvider,
    private val transactionProvider: TransactionProvider,
    private val logger: Logger,
) {
    suspend fun move(
        itemId: StashEntryId,
        targetStashId: StashDefinitionId,
    ): Result<StashEntry, MoveStashItemError> {
        val ownerId = stashOwnerProvider.current()
        return transactionProvider.withTransaction {
            val stashes = stashRepository.observeStashes(ownerId).first()
            val stashById = stashes.associateBy { it.id }
            val item = stashRepository.getItem(itemId)
            if (item == null || item.stashId !in stashById) {
                return@withTransaction logger.logAndReturnFailure(
                    tag = TAG,
                    error = MoveStashItemError.ItemNotFound(itemId),
                    message = { "Stash item $itemId not found for owner $ownerId." },
                )
            }

            val currentStash = stashById.getValue(item.stashId)
            val targetStash =
                stashById[targetStashId] ?: return@withTransaction logger.logAndReturnFailure(
                    tag = TAG,
                    error = MoveStashItemError.TargetStashNotFound(targetStashId),
                    message = { "Cannot move stash item $itemId to unknown stash $targetStashId." },
                )

            if (currentStash.id == targetStash.id) {
                return@withTransaction logger.logAndReturnFailure(
                    tag = TAG,
                    error = MoveStashItemError.AlreadyInTargetStash(targetStashId),
                    message = { "Stash item $itemId is already in stash $targetStashId." },
                )
            }

            val moved = item.copy(stashId = targetStashId)
            stashRepository.updateItem(moved)

            val movedAt = dateProvider.now()
            stashRepository.insertMovement(
                StashMovement.new(
                    stashId = currentStash.id,
                    itemId = item.id,
                    operation = StashMovementOperation.ManualAdjust,
                    measurementChange = item.measurement.negate(),
                    linkedDiaryEntryId = null,
                    createdAt = movedAt,
                    note = "Moved to ${targetStash.name.value}",
                )
            )
            stashRepository.insertMovement(
                StashMovement.new(
                    stashId = targetStash.id,
                    itemId = item.id,
                    operation = StashMovementOperation.ManualAdjust,
                    measurementChange = item.measurement,
                    linkedDiaryEntryId = null,
                    createdAt = movedAt,
                    note = "Moved from ${currentStash.name.value}",
                )
            )
            Ok(moved)
        }
    }

    private companion object {
        const val TAG = "MoveStashItemUseCase"
    }
}
