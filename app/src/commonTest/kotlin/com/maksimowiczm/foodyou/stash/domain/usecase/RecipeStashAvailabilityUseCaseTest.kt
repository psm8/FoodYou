package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.result.Result.Success
import com.maksimowiczm.foodyou.food.domain.entity.Recipe
import com.maksimowiczm.foodyou.food.domain.entity.RecipeIngredient
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

class RecipeStashAvailabilityUseCaseTest {
    @Test
    fun when_only_some_ingredients_are_available_then_availability_reports_partial_summary() = runBlocking {
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
                note = null,
                isLiquid = false,
            )
        val useCase =
            AssessRecipeStashAvailabilityUseCase(
                recipeRepository = FakeRecipeRepository(listOf(recipe)),
                stashRepository =
                    FakeStashRepository(
                        initialStashes = listOf(sampleStash()),
                        initialItems =
                            listOf(
                                sampleRawProductItem(
                                    id = 1,
                                    measurement = StashMeasurement.grams(250.0),
                                    product = flour,
                                ),
                                sampleRawProductItem(
                                    id = 2,
                                    measurement = StashMeasurement.milliliters(50.0),
                                    product = tomatoSauce,
                                ),
                            ),
                    ),
                stashOwnerProvider = localOwnerProvider(),
                logger = NoOpLogger,
            )

        val result =
            useCase.assess(
                recipeId = recipe.id,
                measurement = Measurement.Serving(2.0),
            )

        val success =
            assertIs<Success<RecipeStashAvailability, AssessRecipeStashAvailabilityError>>(result)
        assertEquals(1, success.data.availableIngredientsCount)
        assertEquals(1, success.data.partiallyAvailableIngredientsCount)
        assertEquals(1, success.data.unavailableIngredientsCount)
    }

    @Test
    fun when_visible_stash_contains_recipe_entries_then_only_matching_product_entries_are_allocated() = runBlocking {
        val flour = sampleProduct(id = 1, name = "Flour", brand = null)
        val nestedRecipe = sampleRecipe(id = 9, name = "Prepared dough")
        val recipe =
            Recipe(
                id = com.maksimowiczm.foodyou.food.domain.entity.FoodId.Recipe(1),
                name = "Pizza",
                servings = 2,
                ingredients = listOf(RecipeIngredient(flour, Measurement.Gram(200.0))),
                note = null,
                isLiquid = false,
            )
        val useCase =
            AssessRecipeStashAvailabilityUseCase(
                recipeRepository = FakeRecipeRepository(listOf(recipe)),
                stashRepository =
                    FakeStashRepository(
                        initialStashes = listOf(sampleStash()),
                        initialItems =
                            listOf(
                                sampleAnonymousDishItem(
                                    id = 1,
                                    measurement = StashMeasurement.servings(1.0),
                                    recipe = nestedRecipe,
                                ),
                                sampleRawProductItem(
                                    id = 2,
                                    measurement = StashMeasurement.grams(250.0),
                                    product = flour,
                                ),
                            ),
                    ),
                stashOwnerProvider = localOwnerProvider(),
                logger = NoOpLogger,
            )

        val result =
            useCase.assess(
                recipeId = recipe.id,
                measurement = Measurement.Serving(2.0),
            )

        val success =
            assertIs<Success<RecipeStashAvailability, AssessRecipeStashAvailabilityError>>(result)
        val ingredient = success.data.ingredients.single()
        assertEquals(IngredientAvailabilityStatus.Available, ingredient.status)
        assertEquals(1, ingredient.allocations.size)
        assertEquals(2L, ingredient.allocations.single().item.id.value)
    }
}
