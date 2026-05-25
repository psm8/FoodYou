package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.common.log.logAndReturnFailure
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinition
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashName
import com.maksimowiczm.foodyou.stash.domain.repository.StashOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import kotlinx.coroutines.flow.first

sealed interface RenameStashError {
    data object InvalidName : RenameStashError

    data class StashNotFound(val stashId: StashDefinitionId) : RenameStashError

    data class DuplicateName(val name: String) : RenameStashError
}

class RenameStashUseCase(
    private val stashRepository: StashRepository,
    private val stashOwnerProvider: StashOwnerProvider,
    private val transactionProvider: TransactionProvider,
    private val logger: Logger,
) {
    suspend fun rename(
        stashId: StashDefinitionId,
        name: String,
    ): Result<StashDefinition, RenameStashError> {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) {
            return logger.logAndReturnFailure(
                tag = TAG,
                error = RenameStashError.InvalidName,
                message = { "Cannot rename stash $stashId to a blank name." },
            )
        }

        val ownerId = stashOwnerProvider.current()
        return transactionProvider.withTransaction {
            val stashes = stashRepository.observeStashes(ownerId).first()
            val existing =
                stashes.firstOrNull { it.id == stashId } ?: return@withTransaction logger.logAndReturnFailure(
                    tag = TAG,
                    error = RenameStashError.StashNotFound(stashId),
                    message = { "Stash $stashId not found for owner $ownerId." },
                )

            if (stashes.any { it.id != stashId && it.name.value == normalizedName }) {
                return@withTransaction logger.logAndReturnFailure(
                    tag = TAG,
                    error = RenameStashError.DuplicateName(normalizedName),
                    message = { "Stash name '$normalizedName' is already used." },
                )
            }

            if (existing.name.value == normalizedName) {
                return@withTransaction Ok(existing)
            }

            val renamed = existing.copy(name = StashName.from(normalizedName))
            stashRepository.updateStash(renamed)
            Ok(renamed)
        }
    }

    private companion object {
        const val TAG = "RenameStashUseCase"
    }
}
