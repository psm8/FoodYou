package com.maksimowiczm.foodyou.app.ui.stash.browser

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
import com.maksimowiczm.foodyou.stash.domain.usecase.AdjustStashItemQuantityError
import com.maksimowiczm.foodyou.stash.domain.usecase.AdjustStashItemQuantityUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.ManualStashAction
import com.maksimowiczm.foodyou.stash.domain.usecase.MoveStashItemError
import com.maksimowiczm.foodyou.stash.domain.usecase.RemoveStashItemError
import com.maksimowiczm.foodyou.stash.domain.usecase.RemoveStashItemUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.StashMeasurementAdjustment
import kotlinx.coroutines.flow.first

internal interface StashBrowserActions {
    suspend fun remove(
        itemId: StashEntryId,
        action: ManualStashAction,
    ): Result<StashEntry, RemoveStashItemError>

    suspend fun adjust(
        itemId: StashEntryId,
        adjustment: StashMeasurementAdjustment,
        action: ManualStashAction,
    ): Result<StashEntry, AdjustStashItemQuantityError>

    suspend fun move(
        itemId: StashEntryId,
        targetStashId: StashDefinitionId,
        action: ManualStashAction,
    ): Result<StashEntry, MoveStashItemError>
}

internal class DomainStashBrowserActions(
    private val adjustStashItemQuantityUseCase: AdjustStashItemQuantityUseCase,
    private val removeStashItemUseCase: RemoveStashItemUseCase,
    private val stashRepository: StashRepository,
    private val stashOwnerProvider: StashOwnerProvider,
    private val dateProvider: DateProvider,
    private val transactionProvider: TransactionProvider,
    private val logger: Logger,
) : StashBrowserActions {
    override suspend fun remove(
        itemId: StashEntryId,
        action: ManualStashAction,
    ): Result<StashEntry, RemoveStashItemError> =
        removeStashItemUseCase.remove(itemId = itemId, action = action)

    override suspend fun adjust(
        itemId: StashEntryId,
        adjustment: StashMeasurementAdjustment,
        action: ManualStashAction,
    ): Result<StashEntry, AdjustStashItemQuantityError> =
        adjustStashItemQuantityUseCase.adjust(itemId = itemId, adjustment = adjustment, action = action)

    override suspend fun move(
        itemId: StashEntryId,
        targetStashId: StashDefinitionId,
        action: ManualStashAction,
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
            val note = action.toMovementNote()
            stashRepository.insertMovement(
                StashMovement.new(
                    stashId = currentStash.id,
                    itemId = item.id,
                    operation = StashMovementOperation.ManualAdjust,
                    measurementChange = item.measurement.negate(),
                    linkedDiaryEntryId = null,
                    createdAt = movedAt,
                    note = note,
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
                    note = note,
                )
            )
            Ok(moved)
        }
    }

    private companion object {
        const val TAG = "DomainStashBrowserActions"
    }
}
