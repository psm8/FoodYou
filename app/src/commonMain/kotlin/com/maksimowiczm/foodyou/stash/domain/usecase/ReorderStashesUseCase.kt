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

sealed interface ReorderStashesError {
    data class OrderMismatch(
        val expected: Set<StashDefinitionId>,
        val actual: Set<StashDefinitionId>,
    ) : ReorderStashesError
}

class ReorderStashesUseCase(
    private val stashRepository: StashRepository,
    private val stashOwnerProvider: StashOwnerProvider,
    private val transactionProvider: TransactionProvider,
    private val logger: Logger,
) {
    suspend fun reorder(order: List<StashDefinitionId>): Result<List<StashDefinition>, ReorderStashesError> {
        val ownerId = stashOwnerProvider.current()
        return transactionProvider.withTransaction {
            val current = stashRepository.observeStashes(ownerId).first()
            val expectedIds = current.map(StashDefinition::id).toSet()
            val actualIds = order.toSet()
            if (current.size != order.size || expectedIds != actualIds) {
                return@withTransaction logger.logAndReturnFailure(
                    tag = TAG,
                    error = ReorderStashesError.OrderMismatch(expectedIds, actualIds),
                    message = { "Received stash order $order, but expected ids $expectedIds." },
                )
            }

            val reordered =
                order.mapIndexed { index, stashId ->
                    current.first { it.id == stashId }.copy(ordering = index)
                }

            reordered.forEach { stashRepository.updateStash(it) }
            Ok(reordered)
        }
    }

    private companion object {
        const val TAG = "ReorderStashesUseCase"
    }
}
