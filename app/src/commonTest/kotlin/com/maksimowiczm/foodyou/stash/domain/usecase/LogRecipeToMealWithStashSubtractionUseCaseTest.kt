package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.result.Result.Success
import com.maksimowiczm.foodyou.food.domain.entity.Recipe
import com.maksimowiczm.foodyou.food.domain.entity.RecipeIngredient
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

class LogRecipeToMealWithStashSubtractionUseCaseTest {
    @Test
    fun when_all_recipe_ingredients_are_available_then_logging_recipe_subtracts_every_ingredient() = runBlocking {
        val flour = sampleProduct(id = 1, name = "Flour", brand = null)
        val cheese = sampleProduct(id = 2, name = "Cheese", brand = null)
        val tomatoSauce = sampleProduct(id = 3, name = "Tomato sauce", brand = null, isLiquid = true)
        val recipe =
            Recipe(
                id = com.maksimowiczm.foodyou.food.domain.entity.FoodId.Recipe(1),
                name = "Pizza",
                servings = 2,
                ingredients =
                    listOf(
                        RecipeIngredient(flour, Measurement.Gram(200.0)),
                        RecipeIngredient(cheese, Measurement.Gram(100.0)),
                        RecipeIngredient(tomatoSauce, Measurement.Milliliter(80.0)),
                    ),
                note = "Stone baked",
                isLiquid = false,
            )
        val stashRepository =
            FakeStashRepository(
                initialStashes = listOf(sampleStash()),
                initialItems =
                    listOf(
                        sampleRawProductItem(id = 1, quantity = StashQuantity.grams(300.0), product = flour),
                        sampleRawProductItem(id = 2, quantity = StashQuantity.grams(150.0), product = cheese),
                        sampleRawProductItem(
                            id = 3,
                            quantity = StashQuantity.milliliters(100.0),
                            product = tomatoSauce,
                        ),
                    ),
            )
        val diaryRepository = FakeFoodDiaryEntryRepository()
        val useCase =
            LogRecipeToMealWithStashSubtractionUseCase(
                recipeRepository = FakeRecipeRepository(listOf(recipe)),
                entryRepository = diaryRepository,
                mealRepository = FakeMealRepository(listOf(sampleMeal())),
                stashRepository = stashRepository,
                stashOwnerProvider = localOwnerProvider(),
                transactionProvider = FakeTransactionProvider(),
                dateProvider = FixedDateProvider(),
                logger = NoOpLogger,
            )

        val result =
            useCase.log(
                recipeId = recipe.id,
                measurement = Measurement.Serving(2.0),
                mealId = sampleMeal().id,
                date = FIXED_NOW.date,
                subtractMode = StashSubtractionMode.Auto,
            )

        val success =
            assertIs<
                Success<LogRecipeToMealWithStashSubtractionResult, LogRecipeToMealWithStashSubtractionError>
            >(result)
        assertEquals(1L, success.data.entryId.value)
        assertEquals(true, success.data.availability.areAllIngredientsAvailable)
        assertEquals(
            listOf(
                StashQuantity.grams(200.0),
                StashQuantity.grams(100.0),
                StashQuantity.milliliters(80.0),
            ),
            stashRepository.allMovements().map { it.quantityChange.negate() },
        )
        assertEquals(
            listOf(
                StashQuantity.grams(100.0),
                StashQuantity.grams(50.0),
                StashQuantity.milliliters(20.0),
            ),
            stashRepository.allItems().sortedBy { it.id.value }.map { it.quantity },
        )
        assertEquals("Pizza", diaryRepository.allEntries().single().food.name)
    }
}
