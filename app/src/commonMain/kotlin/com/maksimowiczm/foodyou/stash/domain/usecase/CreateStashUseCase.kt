package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.common.log.logAndReturnFailure
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinition
import com.maksimowiczm.foodyou.stash.domain.entity.StashName
import com.maksimowiczm.foodyou.stash.domain.repository.StashOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import kotlinx.coroutines.flow.first

sealed interface CreateStashError {
    data object InvalidName : CreateStashError

    data class DuplicateName(val name: String) : CreateStashError
}

class CreateStashUseCase(
    private val stashRepository: StashRepository,
    private val stashOwnerProvider: StashOwnerProvider,
    private val dateProvider: DateProvider,
    private val transactionProvider: TransactionProvider,
    private val logger: Logger,
) {
    suspend fun create(name: String): Result<StashDefinition, CreateStashError> {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) {
            return logger.logAndReturnFailure(
                tag = TAG,
                error = CreateStashError.InvalidName,
                message = { "Cannot create stash with a blank name." },
            )
        }

        val ownerId = stashOwnerProvider.current()
        return transactionProvider.withTransaction {
            val stashes = stashRepository.observeStashes(ownerId).first()
            if (stashes.any { it.name.value == normalizedName }) {
                return@withTransaction logger.logAndReturnFailure(
                    tag = TAG,
                    error = CreateStashError.DuplicateName(normalizedName),
                    message = { "Stash name '$normalizedName' is already used." },
                )
            }

            val created =
                StashDefinition.new(
                    ownerId = ownerId,
                    name = StashName.from(normalizedName),
                    createdAt = dateProvider.now(),
                    ordering = stashes.size,
                )

            val stashId = stashRepository.insertStash(created)
            Ok(created.copy(id = stashId))
        }
    }

    private companion object {
        const val TAG = "CreateStashUseCase"
    }
}
