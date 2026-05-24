package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement

sealed interface AddRecipeToStashError {
    data class RecipeNotFound(val id: FoodId.Recipe) : AddRecipeToStashError

    data class StashNotFound(val id: StashDefinitionId) : AddRecipeToStashError

    data object StashSelectionRequired : AddRecipeToStashError

    data object NonPositiveQuantity : AddRecipeToStashError

    data object NonPositiveServings : AddRecipeToStashError
}

data class AddRecipeToStashResult(
    val stashId: StashDefinitionId,
    val itemId: StashEntryId,
)

class AddRecipeToStashUseCase(
    private val createAnonymousDishSnapshotUseCase: CreateAnonymousDishSnapshotUseCase,
) {
    suspend fun add(
        recipeId: FoodId.Recipe,
        stashId: StashDefinitionId?,
        totalAmount: StashMeasurement,
        servings: Int,
    ): Result<AddRecipeToStashResult, AddRecipeToStashError> =
        when (
            val result =
                createAnonymousDishSnapshotUseCase.create(
                    recipeId = recipeId,
                    stashId = stashId,
                    totalAmount = totalAmount,
                    servings = servings,
                )
        ) {
            is Result.Success ->
                Result.Success(
                    AddRecipeToStashResult(
                        stashId = result.data.stashId,
                        itemId = result.data.itemId,
                    )
                )

            is Result.Error -> Result.Error(result.error.toAddRecipeToStashError())
        }
}

private fun CreateAnonymousDishSnapshotError.toAddRecipeToStashError(): AddRecipeToStashError =
    when (this) {
        is CreateAnonymousDishSnapshotError.RecipeNotFound -> AddRecipeToStashError.RecipeNotFound(id)
        is CreateAnonymousDishSnapshotError.StashNotFound -> AddRecipeToStashError.StashNotFound(id)
        CreateAnonymousDishSnapshotError.StashSelectionRequired -> AddRecipeToStashError.StashSelectionRequired
        CreateAnonymousDishSnapshotError.NonPositiveQuantity -> AddRecipeToStashError.NonPositiveQuantity
        CreateAnonymousDishSnapshotError.NonPositiveServings -> AddRecipeToStashError.NonPositiveServings
    }
