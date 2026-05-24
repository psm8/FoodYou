package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.common.log.logAndReturnFailure
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.repository.RecipeRepository
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntry
import com.maksimowiczm.foodyou.stash.domain.repository.StashOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import kotlinx.coroutines.flow.first

sealed interface AssessRecipeStashAvailabilityError {
    data class RecipeNotFound(val id: FoodId.Recipe) : AssessRecipeStashAvailabilityError
}

class AssessRecipeStashAvailabilityUseCase(
    private val recipeRepository: RecipeRepository,
    private val stashRepository: StashRepository,
    private val stashOwnerProvider: StashOwnerProvider,
    private val logger: Logger,
) {
    suspend fun assess(
        recipeId: FoodId.Recipe,
        measurement: Measurement,
    ): Result<RecipeStashAvailability, AssessRecipeStashAvailabilityError> {
        val recipe = recipeRepository.observeRecipe(recipeId).first()
        if (recipe == null) {
            return logger.logAndReturnFailure(
                tag = TAG,
                error = AssessRecipeStashAvailabilityError.RecipeNotFound(recipeId),
                message = { "Recipe with id $recipeId not found." },
            )
        }

        val candidateItems = loadVisibleStashItems()
        return Ok(buildRecipeStashAvailability(recipe, measurement, candidateItems))
    }

    private suspend fun loadVisibleStashItems(): List<StashEntry> =
        stashRepository
            .observeStashes(stashOwnerProvider.current())
            .first()
            .flatMap { stash -> stashRepository.observeStashContents(stash.id).first() }

    private companion object {
        const val TAG = "AssessRecipeStashAvailabilityUseCase"
    }
}
