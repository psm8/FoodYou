package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.common.log.logAndReturnFailure
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.stash.domain.entity.ShoppingSession
import com.maksimowiczm.foodyou.stash.domain.entity.ShoppingSessionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.repository.StashOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import kotlinx.coroutines.flow.first

sealed interface StartShoppingSessionError {
    data class StashNotFound(val id: StashDefinitionId) : StartShoppingSessionError
}

class StartShoppingSessionUseCase(
    private val stashRepository: StashRepository,
    private val stashOwnerProvider: StashOwnerProvider,
    private val dateProvider: DateProvider,
    private val logger: Logger,
) {
    suspend fun start(stashId: StashDefinitionId): Result<ShoppingSession, StartShoppingSessionError> {
        val ownerId = stashOwnerProvider.current()
        val stash = stashRepository.observeStashes(ownerId).first().firstOrNull { it.id == stashId }
        if (stash == null) {
            return logger.logAndReturnFailure(
                tag = TAG,
                error = StartShoppingSessionError.StashNotFound(stashId),
                message = { "Stash with id $stashId not found." },
            )
        }

        return Ok(
            ShoppingSession(
                id = ShoppingSessionId("stash-${stashId.value}-${dateProvider.nowInstant()}"),
                stashId = stash.id,
                items = emptyList(),
            )
        )
    }

    private companion object {
        const val TAG = "StartShoppingSessionUseCase"
    }
}
