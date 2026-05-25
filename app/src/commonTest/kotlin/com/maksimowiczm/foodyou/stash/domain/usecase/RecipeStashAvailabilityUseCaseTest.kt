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

    @Test
    fun when_direct_sub_recipe_has_matching_stash_recipe_entry_then_it_is_allocated_without_flattening() = runBlocking {
        val flour = sampleProduct(id = 1, name = "Flour", brand = null)
        val dough =
            Recipe(
                id = com.maksimowiczm.foodyou.food.domain.entity.FoodId.Recipe(9),
                name = "Prepared dough",
                servings = 2,
                ingredients = listOf(RecipeIngredient(flour, Measurement.Gram(200.0))),
                note = null,
                isLiquid = false,
            )
        val parentRecipe =
            Recipe(
                id = com.maksimowiczm.foodyou.food.domain.entity.FoodId.Recipe(10),
                name = "Pizza",
                servings = 2,
                ingredients = listOf(RecipeIngredient(dough, Measurement.Serving(1.0))),
                note = null,
                isLiquid = false,
            )
        val useCase =
            AssessRecipeStashAvailabilityUseCase(
                recipeRepository = FakeRecipeRepository(listOf(parentRecipe, dough)),
                stashRepository =
                    FakeStashRepository(
                        initialStashes = listOf(sampleStash()),
                        initialItems =
                            listOf(
                                sampleAnonymousDishItem(
                                    id = 1,
                                    measurement = StashMeasurement.servings(1.0),
                                    recipe = dough,
                                    totalAmount = Measurement.Serving(2.0),
                                    servingsMade = 2,
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
                recipeId = parentRecipe.id,
                measurement = Measurement.Serving(2.0),
            )

        val success =
            assertIs<Success<RecipeStashAvailability, AssessRecipeStashAvailabilityError>>(result)
        val ingredient = success.data.ingredients.single()
        assertEquals(IngredientAvailabilityStatus.Available, ingredient.status)
        assertEquals(1, success.data.ingredients.size)
        assertEquals(1, ingredient.allocations.size)
        assertEquals(1L, ingredient.allocations.single().item.id.value)
    }

    @Test
    fun when_direct_sub_recipe_is_only_partially_available_then_product_entries_are_not_used_as_fallback() = runBlocking {
        val flour = sampleProduct(id = 1, name = "Flour", brand = null)
        val dough =
            Recipe(
                id = com.maksimowiczm.foodyou.food.domain.entity.FoodId.Recipe(9),
                name = "Prepared dough",
                servings = 2,
                ingredients = listOf(RecipeIngredient(flour, Measurement.Gram(200.0))),
                note = null,
                isLiquid = false,
            )
        val parentRecipe =
            Recipe(
                id = com.maksimowiczm.foodyou.food.domain.entity.FoodId.Recipe(10),
                name = "Pizza",
                servings = 2,
                ingredients = listOf(RecipeIngredient(dough, Measurement.Serving(1.0))),
                note = null,
                isLiquid = false,
            )
        val useCase =
            AssessRecipeStashAvailabilityUseCase(
                recipeRepository = FakeRecipeRepository(listOf(parentRecipe, dough)),
                stashRepository =
                    FakeStashRepository(
                        initialStashes = listOf(sampleStash()),
                        initialItems =
                            listOf(
                                sampleAnonymousDishItem(
                                    id = 1,
                                    measurement = StashMeasurement.servings(0.5),
                                    recipe = dough,
                                    totalAmount = Measurement.Serving(2.0),
                                    servingsMade = 2,
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
                recipeId = parentRecipe.id,
                measurement = Measurement.Serving(2.0),
            )

        val success =
            assertIs<Success<RecipeStashAvailability, AssessRecipeStashAvailabilityError>>(result)
        val ingredient = success.data.ingredients.single()
        assertEquals(IngredientAvailabilityStatus.PartiallyAvailable, ingredient.status)
        assertEquals(StashMeasurement.servings(0.5), ingredient.availableMeasurement)
        assertEquals(listOf(1L), ingredient.allocations.map { it.item.id.value })
    }

    @Test
    fun when_direct_sub_recipe_stash_entry_uses_servings_then_weight_requirement_is_allocated_from_recipe_entry() = runBlocking {
        val flour = sampleProduct(id = 1, name = "Flour", brand = null)
        val dough =
            Recipe(
                id = com.maksimowiczm.foodyou.food.domain.entity.FoodId.Recipe(9),
                name = "Prepared dough",
                servings = 2,
                ingredients = listOf(RecipeIngredient(flour, Measurement.Gram(400.0))),
                note = null,
                isLiquid = false,
            )
        val parentRecipe =
            Recipe(
                id = com.maksimowiczm.foodyou.food.domain.entity.FoodId.Recipe(10),
                name = "Pizza",
                servings = 2,
                ingredients = listOf(RecipeIngredient(dough, Measurement.Gram(200.0))),
                note = null,
                isLiquid = false,
            )
        val useCase =
            AssessRecipeStashAvailabilityUseCase(
                recipeRepository = FakeRecipeRepository(listOf(parentRecipe, dough)),
                stashRepository =
                    FakeStashRepository(
                        initialStashes = listOf(sampleStash()),
                        initialItems =
                            listOf(
                                sampleAnonymousDishItem(
                                    id = 1,
                                    measurement = StashMeasurement.servings(1.0),
                                    recipe = dough,
                                    totalAmount = Measurement.Serving(2.0),
                                    servingsMade = 2,
                                ),
                                sampleRawProductItem(
                                    id = 2,
                                    measurement = StashMeasurement.grams(500.0),
                                    product = flour,
                                ),
                            ),
                    ),
                stashOwnerProvider = localOwnerProvider(),
                logger = NoOpLogger,
            )

        val result =
            useCase.assess(
                recipeId = parentRecipe.id,
                measurement = Measurement.Serving(2.0),
            )

        val success =
            assertIs<Success<RecipeStashAvailability, AssessRecipeStashAvailabilityError>>(result)
        val ingredient = success.data.ingredients.single()
        assertEquals(IngredientAvailabilityStatus.Available, ingredient.status)
        assertEquals(StashMeasurement.grams(200.0), ingredient.requiredMeasurement)
        assertEquals(StashMeasurement.grams(200.0), ingredient.availableMeasurement)
        assertEquals(1, ingredient.allocations.size)
        assertEquals(1L, ingredient.allocations.single().item.id.value)
        assertEquals(StashMeasurement.servings(1.0), ingredient.allocations.single().measurement)
    }

    @Test
    fun when_intermediate_recipe_has_no_stash_match_then_lower_level_recipe_match_is_used() = runBlocking {
        val tomatoSauceBase = sampleProduct(id = 1, name = "Tomatoes", brand = null, isLiquid = true)
        val sauce =
            Recipe(
                id = com.maksimowiczm.foodyou.food.domain.entity.FoodId.Recipe(20),
                name = "Sauce",
                servings = 1,
                ingredients = listOf(RecipeIngredient(tomatoSauceBase, Measurement.Milliliter(100.0))),
                note = null,
                isLiquid = true,
            )
        val filling =
            Recipe(
                id = com.maksimowiczm.foodyou.food.domain.entity.FoodId.Recipe(10),
                name = "Filling",
                servings = 1,
                ingredients = listOf(RecipeIngredient(sauce, Measurement.Serving(1.0))),
                note = null,
                isLiquid = false,
            )
        val parentRecipe =
            Recipe(
                id = com.maksimowiczm.foodyou.food.domain.entity.FoodId.Recipe(30),
                name = "Lasagna",
                servings = 1,
                ingredients = listOf(RecipeIngredient(filling, Measurement.Serving(1.0))),
                note = null,
                isLiquid = false,
            )
        val useCase =
            AssessRecipeStashAvailabilityUseCase(
                recipeRepository = FakeRecipeRepository(listOf(parentRecipe, filling, sauce)),
                stashRepository =
                    FakeStashRepository(
                        initialStashes = listOf(sampleStash()),
                        initialItems =
                            listOf(
                                sampleAnonymousDishItem(
                                    id = 1,
                                    measurement = StashMeasurement.servings(1.0),
                                    recipe = sauce,
                                    totalAmount = Measurement.Serving(1.0),
                                    servingsMade = 1,
                                ),
                            ),
                    ),
                stashOwnerProvider = localOwnerProvider(),
                logger = NoOpLogger,
            )

        val result =
            useCase.assess(
                recipeId = parentRecipe.id,
                measurement = Measurement.Serving(1.0),
            )

        val success =
            assertIs<Success<RecipeStashAvailability, AssessRecipeStashAvailabilityError>>(result)
        val ingredient = success.data.ingredients.single()
        assertEquals(sauce.id, ingredient.foodId)
        assertEquals(IngredientAvailabilityStatus.Available, ingredient.status)
        assertEquals(StashMeasurement.servings(1.0), ingredient.requiredMeasurement)
        assertEquals(listOf(1L), ingredient.allocations.map { it.item.id.value })
    }

    @Test
    fun when_nested_recipe_mix_requires_recipe_match_and_product_fallback_then_duplicate_product_requirements_stay_grouped() =
        runBlocking {
            val flour = sampleProduct(id = 1, name = "Flour", brand = null)
            val tomatoSauceBase = sampleProduct(id = 2, name = "Tomatoes", brand = null, isLiquid = true)
            val sauce =
                Recipe(
                    id = com.maksimowiczm.foodyou.food.domain.entity.FoodId.Recipe(20),
                    name = "Sauce",
                    servings = 1,
                    ingredients = listOf(RecipeIngredient(tomatoSauceBase, Measurement.Milliliter(100.0))),
                    note = null,
                    isLiquid = true,
                )
            val dough =
                Recipe(
                    id = com.maksimowiczm.foodyou.food.domain.entity.FoodId.Recipe(11),
                    name = "Dough",
                    servings = 1,
                    ingredients = listOf(RecipeIngredient(flour, Measurement.Gram(100.0))),
                    note = null,
                    isLiquid = false,
                )
            val filling =
                Recipe(
                    id = com.maksimowiczm.foodyou.food.domain.entity.FoodId.Recipe(10),
                    name = "Filling",
                    servings = 1,
                    ingredients =
                        listOf(
                            RecipeIngredient(sauce, Measurement.Serving(1.0)),
                            RecipeIngredient(dough, Measurement.Serving(1.0)),
                            RecipeIngredient(flour, Measurement.Gram(50.0)),
                        ),
                    note = null,
                    isLiquid = false,
                )
            val parentRecipe =
                Recipe(
                    id = com.maksimowiczm.foodyou.food.domain.entity.FoodId.Recipe(30),
                    name = "Pizza",
                    servings = 1,
                    ingredients = listOf(RecipeIngredient(filling, Measurement.Serving(1.0))),
                    note = null,
                    isLiquid = false,
                )
            val useCase =
                AssessRecipeStashAvailabilityUseCase(
                    recipeRepository = FakeRecipeRepository(listOf(parentRecipe, filling, dough, sauce)),
                    stashRepository =
                        FakeStashRepository(
                            initialStashes = listOf(sampleStash()),
                            initialItems =
                                listOf(
                                    sampleAnonymousDishItem(
                                        id = 1,
                                        measurement = StashMeasurement.servings(1.0),
                                        recipe = sauce,
                                        totalAmount = Measurement.Serving(1.0),
                                        servingsMade = 1,
                                    ),
                                    sampleRawProductItem(
                                        id = 2,
                                        measurement = StashMeasurement.grams(160.0),
                                        product = flour,
                                    ),
                                ),
                        ),
                    stashOwnerProvider = localOwnerProvider(),
                    logger = NoOpLogger,
                )

            val result =
                useCase.assess(
                    recipeId = parentRecipe.id,
                    measurement = Measurement.Serving(1.0),
                )

            val success =
                assertIs<Success<RecipeStashAvailability, AssessRecipeStashAvailabilityError>>(result)
            assertEquals(2, success.data.ingredients.size)

            val sauceAvailability = success.data.ingredients.first { it.foodId == sauce.id }
            assertEquals(IngredientAvailabilityStatus.Available, sauceAvailability.status)
            assertEquals(StashMeasurement.servings(1.0), sauceAvailability.requiredMeasurement)
            assertEquals(listOf(1L), sauceAvailability.allocations.map { it.item.id.value })

            val flourAvailability = success.data.ingredients.first { it.foodId == flour.id }
            assertEquals(IngredientAvailabilityStatus.Available, flourAvailability.status)
            assertEquals(StashMeasurement.grams(150.0), flourAvailability.requiredMeasurement)
            assertEquals(StashMeasurement.grams(150.0), flourAvailability.availableMeasurement)
            assertEquals(listOf(2L), flourAvailability.allocations.map { it.item.id.value })
        }

    @Test
    fun when_nested_recipe_path_revisits_root_recipe_then_cycle_is_reported_as_unavailable() = runBlocking {
        val flour = sampleProduct(id = 1, name = "Flour", brand = null)
        val cycleAReference =
            Recipe(
                id = com.maksimowiczm.foodyou.food.domain.entity.FoodId.Recipe(10),
                name = "Cycle A",
                servings = 1,
                ingredients = listOf(RecipeIngredient(flour, Measurement.Gram(100.0))),
                note = null,
                isLiquid = false,
            )
        val cycleB =
            Recipe(
                id = com.maksimowiczm.foodyou.food.domain.entity.FoodId.Recipe(11),
                name = "Cycle B",
                servings = 1,
                ingredients = listOf(RecipeIngredient(cycleAReference, Measurement.Serving(1.0))),
                note = null,
                isLiquid = false,
            )
        val cycleA =
            Recipe(
                id = com.maksimowiczm.foodyou.food.domain.entity.FoodId.Recipe(10),
                name = "Cycle A",
                servings = 1,
                ingredients = listOf(RecipeIngredient(cycleB, Measurement.Serving(1.0))),
                note = null,
                isLiquid = false,
            )

        val useCase =
            AssessRecipeStashAvailabilityUseCase(
                recipeRepository = FakeRecipeRepository(listOf(cycleA, cycleB)),
                stashRepository = FakeStashRepository(initialStashes = listOf(sampleStash())),
                stashOwnerProvider = localOwnerProvider(),
                logger = NoOpLogger,
            )

        val result =
            useCase.assess(
                recipeId = cycleA.id,
                measurement = Measurement.Serving(1.0),
            )

        val success =
            assertIs<Success<RecipeStashAvailability, AssessRecipeStashAvailabilityError>>(result)
        val ingredient = success.data.ingredients.single()
        assertEquals(cycleA.id, ingredient.foodId)
        assertEquals("Cycle A", ingredient.foodName)
        assertEquals(IngredientAvailabilityStatus.Unavailable, ingredient.status)
        assertEquals(StashMeasurement.servings(1.0), ingredient.requiredMeasurement)
        assertEquals(emptyList(), ingredient.allocations)
    }
}
