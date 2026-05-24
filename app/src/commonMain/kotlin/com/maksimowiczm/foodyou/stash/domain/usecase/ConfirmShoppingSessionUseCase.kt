package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.common.log.logAndReturnFailure
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.stash.domain.entity.ShoppingSession
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntry
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import com.maksimowiczm.foodyou.stash.domain.repository.StashOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import kotlinx.coroutines.flow.first

sealed interface ConfirmShoppingSessionError {
    data object EmptySession : ConfirmShoppingSessionError

    data class StashNotFound(val id: com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId) :
        ConfirmShoppingSessionError

    data object Unknown : ConfirmShoppingSessionError
}

class ConfirmShoppingSessionUseCase(
    private val stashRepository: StashRepository,
    private val stashOwnerProvider: StashOwnerProvider,
    private val transactionProvider: TransactionProvider,
    private val dateProvider: DateProvider,
    private val logger: Logger,
) {
    suspend fun confirm(
        session: ShoppingSession,
    ): Result<List<StashEntryId>, ConfirmShoppingSessionError> {
        if (session.items.isEmpty()) {
            return logger.logAndReturnFailure(
                tag = TAG,
                error = ConfirmShoppingSessionError.EmptySession,
                message = { "Shopping session ${session.id.value} has no items to confirm." },
            )
        }

        return try {
            transactionProvider.withTransaction {
                val ownerId = stashOwnerProvider.current()
                val stash = stashRepository.observeStashes(ownerId).first().firstOrNull { it.id == session.stashId }
                if (stash == null) {
                    return@withTransaction logger.logAndReturnFailure(
                        tag = TAG,
                        error = ConfirmShoppingSessionError.StashNotFound(session.stashId),
                        message = { "Stash with id ${session.stashId} not found." },
                    )
                }

                val now = dateProvider.now()
                val itemIds =
                    session.items.map { item ->
                        val itemId =
                            stashRepository.insertItem(
                                StashEntry.new(
                                    stashId = stash.id,
                                    foodRef = item.foodRef,
                                    measurement = item.measurement,
                                    createdAt = now,
                                )
                            )
                        stashRepository.insertMovement(
                            StashMovement.new(
                                stashId = stash.id,
                                itemId = itemId,
                                operation = StashMovementOperation.Purchase,
                                measurementChange = item.measurement,
                                linkedDiaryEntryId = null,
                                createdAt = now,
                            )
                        )
                        itemId
                    }

                Ok(itemIds)
            }
        } catch (exception: Exception) {
            logger.logAndReturnFailure(
                tag = TAG,
                error = ConfirmShoppingSessionError.Unknown,
                throwable = exception,
                message = { "Failed to confirm shopping session ${session.id.value}." },
            )
        }
    }

    private companion object {
        const val TAG = "ConfirmShoppingSessionUseCase"
    }
}
