package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.common.log.logAndReturnFailure
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinition
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.repository.StashOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import kotlinx.coroutines.flow.first

sealed interface DeleteStashError {
    data class StashNotFound(val stashId: StashDefinitionId) : DeleteStashError
}

class DeleteStashUseCase(
    private val stashRepository: StashRepository,
    private val stashOwnerProvider: StashOwnerProvider,
    private val transactionProvider: TransactionProvider,
    private val logger: Logger,
) {
    suspend fun delete(stashId: StashDefinitionId): Result<Unit, DeleteStashError> {
        val ownerId = stashOwnerProvider.current()
        return transactionProvider.withTransaction {
            val stashes = stashRepository.observeStashes(ownerId).first()
            if (stashes.none { it.id == stashId }) {
                return@withTransaction logger.logAndReturnFailure(
                    tag = TAG,
                    error = DeleteStashError.StashNotFound(stashId),
                    message = { "Stash $stashId not found for owner $ownerId." },
                )
            }

            stashRepository.deleteStash(stashId)
            compactOrdering(
                stashes = stashes.filterNot { it.id == stashId },
                stashRepository = stashRepository,
            )
            Ok()
        }
    }

    private suspend fun compactOrdering(
        stashes: List<StashDefinition>,
        stashRepository: StashRepository,
    ) {
        stashes
            .sortedWith(compareBy<StashDefinition>({ it.ordering }, { it.createdAt }, { it.id.value }))
            .mapIndexed { index, stash -> stash.copy(ordering = index) }
            .forEach { stashRepository.updateStash(it) }
    }

    private companion object {
        const val TAG = "DeleteStashUseCase"
    }
}
