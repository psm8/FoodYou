package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.common.log.logAndReturnFailure
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.repository.RecipeRepository
import com.maksimowiczm.foodyou.stash.domain.entity.AnonymousDishSnapshot
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinition
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashItem
import com.maksimowiczm.foodyou.stash.domain.entity.StashItemId
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import com.maksimowiczm.foodyou.stash.domain.entity.StashName
import com.maksimowiczm.foodyou.stash.domain.repository.StashOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import kotlinx.coroutines.flow.first

sealed interface CreateAnonymousDishSnapshotError {
    data class RecipeNotFound(val id: FoodId.Recipe) : CreateAnonymousDishSnapshotError

    data class StashNotFound(val id: StashDefinitionId) : CreateAnonymousDishSnapshotError

    data object StashSelectionRequired : CreateAnonymousDishSnapshotError

    data object NonPositiveQuantity : CreateAnonymousDishSnapshotError

    data object NonPositiveServings : CreateAnonymousDishSnapshotError
}

data class CreateAnonymousDishSnapshotResult(
    val stashId: StashDefinitionId,
    val itemId: StashItemId,
)

class CreateAnonymousDishSnapshotUseCase(
    private val recipeRepository: RecipeRepository,
    private val stashRepository: StashRepository,
    private val stashOwnerProvider: StashOwnerProvider,
    private val transactionProvider: TransactionProvider,
    private val dateProvider: DateProvider,
    private val logger: Logger,
) {
    suspend fun create(
        recipeId: FoodId.Recipe,
        stashId: StashDefinitionId? = null,
        totalAmount: StashMeasurement,
        servings: Int,
    ): Result<CreateAnonymousDishSnapshotResult, CreateAnonymousDishSnapshotError> {
        if (totalAmount.measurement.rawValue <= 0.0) {
            return logger.logAndReturnFailure(
                tag = TAG,
                error = CreateAnonymousDishSnapshotError.NonPositiveQuantity,
                message = { "Dish total amount must be greater than 0." },
            )
        }

        if (servings <= 0) {
            return logger.logAndReturnFailure(
                tag = TAG,
                error = CreateAnonymousDishSnapshotError.NonPositiveServings,
                message = { "Snapshot servings must be greater than 0." },
            )
        }

        return transactionProvider.withTransaction {
            val recipe = recipeRepository.observeRecipe(recipeId).first()
            if (recipe == null) {
                return@withTransaction logger.logAndReturnFailure(
                    tag = TAG,
                    error = CreateAnonymousDishSnapshotError.RecipeNotFound(recipeId),
                    message = { "Recipe with id $recipeId not found." },
                )
            }

            val ownerId = stashOwnerProvider.current()
            val now = dateProvider.now()
            val stashes = stashRepository.observeStashes(ownerId).first()
            val targetStash =
                when {
                    stashId != null ->
                        stashes.firstOrNull { it.id == stashId } ?: return@withTransaction logger.logAndReturnFailure(
                            tag = TAG,
                            error = CreateAnonymousDishSnapshotError.StashNotFound(stashId),
                            message = { "Stash with id $stashId not found." },
                        )

                    stashes.isEmpty() -> {
                        val defaultStash =
                            StashDefinition.new(
                                ownerId = ownerId,
                                name = StashName.from(DEFAULT_STASH_NAME),
                                createdAt = now,
                                ordering = 0,
                            )
                        val createdStashId = stashRepository.insertStash(defaultStash)
                        defaultStash.copy(id = createdStashId)
                    }

                    stashes.size == 1 -> stashes.single()

                    else ->
                        return@withTransaction logger.logAndReturnFailure(
                            tag = TAG,
                            error = CreateAnonymousDishSnapshotError.StashSelectionRequired,
                            message = { "A stash must be selected when more than one stash exists." },
                        )
                }

            val item =
                StashItem.new(
                    stashId = targetStash.id,
                    snapshot =
                        AnonymousDishSnapshot.from(
                            recipe = recipe,
                            totalAmount = totalAmount.measurement,
                            servingsMade = servings,
                        ),
                    measurement = totalAmount,
                    createdAt = now,
                )
            val itemId = stashRepository.insertItem(item)
            stashRepository.insertMovement(
                StashMovement.new(
                    stashId = targetStash.id,
                    itemId = itemId,
                    operation = StashMovementOperation.CreateSnapshot,
                    measurementChange = totalAmount,
                    linkedDiaryEntryId = null,
                    createdAt = now,
                )
            )

            Ok(CreateAnonymousDishSnapshotResult(stashId = targetStash.id, itemId = itemId))
        }
    }

    private companion object {
        const val TAG = "CreateAnonymousDishSnapshotUseCase"
        const val DEFAULT_STASH_NAME = "Stash"
    }
}

