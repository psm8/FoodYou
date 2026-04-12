package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.common.log.logAndReturnFailure
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.entity.Recipe
import com.maksimowiczm.foodyou.food.domain.entity.RecipeIngredient
import com.maksimowiczm.foodyou.food.domain.repository.RecipeRepository
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFood
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodProduct
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodRecipe
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodRecipeIngredient
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId
import com.maksimowiczm.foodyou.fooddiary.domain.repository.FoodDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.domain.repository.MealRepository
import com.maksimowiczm.foodyou.stash.domain.entity.LinkedDiaryEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import com.maksimowiczm.foodyou.stash.domain.repository.StashOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

sealed interface LogRecipeToMealWithStashSubtractionError {
    data class RecipeNotFound(val id: FoodId.Recipe) : LogRecipeToMealWithStashSubtractionError

    data object MealNotFound : LogRecipeToMealWithStashSubtractionError
}

data class LogRecipeToMealWithStashSubtractionResult(
    val entryId: FoodDiaryEntryId,
    val availability: RecipeStashAvailability,
)

class LogRecipeToMealWithStashSubtractionUseCase(
    private val recipeRepository: RecipeRepository,
    private val entryRepository: FoodDiaryEntryRepository,
    private val mealRepository: MealRepository,
    private val stashRepository: StashRepository,
    private val stashOwnerProvider: StashOwnerProvider,
    private val transactionProvider: TransactionProvider,
    private val dateProvider: DateProvider,
    private val logger: Logger,
) {
    suspend fun log(
        recipeId: FoodId.Recipe,
        measurement: Measurement,
        mealId: Long,
        date: LocalDate,
        subtractMode: StashSubtractionMode,
    ): Result<LogRecipeToMealWithStashSubtractionResult, LogRecipeToMealWithStashSubtractionError> =
        transactionProvider.withTransaction {
            val recipe = recipeRepository.observeRecipe(recipeId).first()
            if (recipe == null) {
                return@withTransaction logger.logAndReturnFailure(
                    tag = TAG,
                    error = LogRecipeToMealWithStashSubtractionError.RecipeNotFound(recipeId),
                    message = { "Recipe with id $recipeId not found." },
                )
            }

            val meal = mealRepository.observeMeal(mealId).first()
            if (meal == null) {
                return@withTransaction logger.logAndReturnFailure(
                    tag = TAG,
                    error = LogRecipeToMealWithStashSubtractionError.MealNotFound,
                    message = { "Meal with id $mealId not found." },
                )
            }

            val availability =
                buildRecipeStashAvailability(
                    recipe = recipe,
                    measurement = measurement,
                    candidateItems = loadVisibleStashItems(),
                )
            val allocations = availability.allocationsFor(subtractMode)

            val now = dateProvider.now()
            val entryId =
                entryRepository.insert(
                    measurement = measurement,
                    mealId = mealId,
                    date = date,
                    food = recipe.toDiaryFood(),
                    createdAt = now,
                )

            allocations.forEach { allocation ->
                val remainingQuantity = allocation.item.quantity - allocation.quantity
                val updatedItem =
                    allocation.item.copy(
                        quantity =
                            if (remainingQuantity.amount <= EPSILON) {
                                allocation.item.quantity.copy(amount = 0.0)
                            } else {
                                remainingQuantity
                            },
                    )
                stashRepository.updateItem(updatedItem)
                stashRepository.insertMovement(
                    StashMovement.new(
                        stashId = allocation.item.stashId,
                        itemId = allocation.item.id,
                        operation = StashMovementOperation.IngredientSubtract,
                        quantityChange = allocation.quantity.negate(),
                        linkedDiaryEntryId = LinkedDiaryEntryId(entryId.value),
                        createdAt = now,
                    )
                )
            }

            Ok(LogRecipeToMealWithStashSubtractionResult(entryId, availability))
        }

    private suspend fun loadVisibleStashItems() =
        stashRepository
            .observeStashes(stashOwnerProvider.current())
            .first()
            .flatMap { stash -> stashRepository.observeStashContents(stash.id).first() }

    private fun RecipeStashAvailability.allocationsFor(
        subtractMode: StashSubtractionMode,
    ): List<IngredientStashAllocation> =
        when (subtractMode) {
            StashSubtractionMode.Skip -> emptyList()
            StashSubtractionMode.Auto ->
                if (areAllIngredientsAvailable) {
                    ingredients.flatMap(RecipeIngredientStashAvailability::allocations)
                } else {
                    emptyList()
                }

            StashSubtractionMode.Partial ->
                ingredients.flatMap(RecipeIngredientStashAvailability::allocations)
        }

    private fun Recipe.toDiaryFood(): DiaryFoodRecipe =
        DiaryFoodRecipe(
            name = headline,
            servings = servings,
            ingredients = ingredients.map { it.toDiaryFoodIngredient() },
            isLiquid = isLiquid,
            note = note,
        )

    private fun RecipeIngredient.toDiaryFoodIngredient(): DiaryFoodRecipeIngredient =
        DiaryFoodRecipeIngredient(
            food = food.toDiaryFood(),
            measurement = measurement,
        )

    private fun com.maksimowiczm.foodyou.food.domain.entity.Food.toDiaryFood(): DiaryFood =
        when (this) {
            is Product ->
                DiaryFoodProduct(
                    name = headline,
                    nutritionFacts = nutritionFacts,
                    servingWeight = servingWeight,
                    totalWeight = totalWeight,
                    isLiquid = isLiquid,
                    source = source,
                    note = note,
                )

            is Recipe -> toDiaryFood()
        }

    private companion object {
        const val TAG = "LogRecipeToMealWithStashSubtractionUseCase"
        const val EPSILON = 0.000001
    }
}
