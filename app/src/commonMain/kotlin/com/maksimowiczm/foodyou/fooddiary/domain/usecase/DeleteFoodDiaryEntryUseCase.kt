package com.maksimowiczm.foodyou.fooddiary.domain.usecase

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.common.log.logAndReturnFailure
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.common.result.onError
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId
import com.maksimowiczm.foodyou.fooddiary.domain.repository.FoodDiaryEntryRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.RestoreLinkedDiaryEntryStashError
import com.maksimowiczm.foodyou.stash.domain.usecase.RestoreLinkedDiaryEntryStashUseCase
import kotlinx.coroutines.flow.firstOrNull

sealed interface DeleteFoodDiaryEntryError {
    data object EntryNotFound : DeleteFoodDiaryEntryError

    data class StashRestoreFailed(
        val error: RestoreLinkedDiaryEntryStashError,
    ) : DeleteFoodDiaryEntryError
}

class DeleteFoodDiaryEntryUseCase(
    private val entryRepository: FoodDiaryEntryRepository,
    private val restoreLinkedDiaryEntryStashUseCase: RestoreLinkedDiaryEntryStashUseCase,
    private val transactionProvider: TransactionProvider,
    private val logger: Logger,
) {
    suspend fun delete(id: FoodDiaryEntryId): Result<Unit, DeleteFoodDiaryEntryError> =
        transactionProvider.withTransaction {
            val entry = entryRepository.observe(id).firstOrNull()
            if (entry == null) {
                return@withTransaction logger.logAndReturnFailure(
                    tag = TAG,
                    error = DeleteFoodDiaryEntryError.EntryNotFound,
                    message = { "Diary entry with id $id not found" },
                )
            }

            restoreLinkedDiaryEntryStashUseCase
                .restoreAll(entry)
                .onError { error ->
                    return@withTransaction logger.logAndReturnFailure(
                        tag = TAG,
                        error = DeleteFoodDiaryEntryError.StashRestoreFailed(error),
                        message = { "Failed to restore linked stash movements for diary entry $id." },
                    )
                }

            entryRepository.delete(id)
            Ok(Unit)
        }

    private companion object {
        const val TAG = "DeleteFoodDiaryEntryUseCase"
    }
}
