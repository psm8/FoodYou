package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.result.Result.Success
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Recipe
import com.maksimowiczm.foodyou.food.domain.entity.RecipeIngredient
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
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
                        sampleRawProductItem(id = 1, measurement = StashMeasurement.grams(300.0), product = flour),
                        sampleRawProductItem(id = 2, measurement = StashMeasurement.grams(150.0), product = cheese),
                        sampleRawProductItem(
                            id = 3,
                            measurement = StashMeasurement.milliliters(100.0),
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
                StashMeasurement.grams(200.0),
                StashMeasurement.grams(100.0),
                StashMeasurement.milliliters(80.0),
            ),
            stashRepository.allMovements().map { it.measurementChange.negate() },
        )
        assertEquals(
            listOf(
                StashMeasurement.grams(100.0),
                StashMeasurement.grams(50.0),
                StashMeasurement.milliliters(20.0),
            ),
            stashRepository.allItems().sortedBy { it.id.value }.map { it.measurement },
        )
        assertEquals("Pizza", diaryRepository.allEntries().single().food.name)
    }

    @Test
    fun when_auto_logging_recipe_with_direct_sub_recipe_match_then_recipe_stash_entry_is_subtracted() = runBlocking {
        val flour = sampleProduct(id = 1, name = "Flour", brand = null)
        val dough =
            Recipe(
                id = FoodId.Recipe(9),
                name = "Prepared dough",
                servings = 2,
                ingredients = listOf(RecipeIngredient(flour, Measurement.Gram(200.0))),
                note = null,
                isLiquid = false,
            )
        val parentRecipe =
            Recipe(
                id = FoodId.Recipe(10),
                name = "Pizza",
                servings = 2,
                ingredients = listOf(RecipeIngredient(dough, Measurement.Serving(1.0))),
                note = null,
                isLiquid = false,
            )
        val stashRepository =
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
            )
        val diaryRepository = FakeFoodDiaryEntryRepository()
        val useCase =
            LogRecipeToMealWithStashSubtractionUseCase(
                recipeRepository = FakeRecipeRepository(listOf(parentRecipe, dough)),
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
                recipeId = parentRecipe.id,
                measurement = Measurement.Serving(2.0),
                mealId = sampleMeal().id,
                date = FIXED_NOW.date,
                subtractMode = StashSubtractionMode.Auto,
            )

        val success =
            assertIs<
                Success<LogRecipeToMealWithStashSubtractionResult, LogRecipeToMealWithStashSubtractionError>
            >(result)
        assertEquals(true, success.data.availability.areAllIngredientsAvailable)
        assertEquals(
            listOf(
                StashMeasurement.servings(0.0),
                StashMeasurement.grams(250.0),
            ),
            stashRepository.allItems().sortedBy { it.id.value }.map { it.measurement },
        )
        val movement = stashRepository.allMovements().single()
        assertEquals(1L, movement.itemId.value)
        assertEquals(
            StashMeasurement.servings(1.0),
            movement.measurementChange.negate(),
        )
        assertEquals("Pizza", diaryRepository.allEntries().single().food.name)
    }

    @Test
    fun when_partial_logging_recipe_with_direct_sub_recipe_match_then_only_recipe_stash_entry_is_subtracted() = runBlocking {
        val flour = sampleProduct(id = 1, name = "Flour", brand = null)
        val cheese = sampleProduct(id = 2, name = "Cheese", brand = null)
        val dough =
            Recipe(
                id = FoodId.Recipe(9),
                name = "Prepared dough",
                servings = 2,
                ingredients = listOf(RecipeIngredient(flour, Measurement.Gram(200.0))),
                note = null,
                isLiquid = false,
            )
        val parentRecipe =
            Recipe(
                id = FoodId.Recipe(10),
                name = "Pizza",
                servings = 2,
                ingredients =
                    listOf(
                        RecipeIngredient(dough, Measurement.Serving(1.0)),
                        RecipeIngredient(cheese, Measurement.Gram(100.0)),
                    ),
                note = null,
                isLiquid = false,
            )
        val stashRepository =
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
            )
        val useCase =
            LogRecipeToMealWithStashSubtractionUseCase(
                recipeRepository = FakeRecipeRepository(listOf(parentRecipe, dough)),
                entryRepository = FakeFoodDiaryEntryRepository(),
                mealRepository = FakeMealRepository(listOf(sampleMeal())),
                stashRepository = stashRepository,
                stashOwnerProvider = localOwnerProvider(),
                transactionProvider = FakeTransactionProvider(),
                dateProvider = FixedDateProvider(),
                logger = NoOpLogger,
            )

        val result =
            useCase.log(
                recipeId = parentRecipe.id,
                measurement = Measurement.Serving(2.0),
                mealId = sampleMeal().id,
                date = FIXED_NOW.date,
                subtractMode = StashSubtractionMode.Partial,
            )

        val success =
            assertIs<
                Success<LogRecipeToMealWithStashSubtractionResult, LogRecipeToMealWithStashSubtractionError>
            >(result)
        assertEquals(false, success.data.availability.areAllIngredientsAvailable)
        assertEquals(
            listOf(
                StashMeasurement.servings(0.0),
                StashMeasurement.grams(250.0),
            ),
            stashRepository.allItems().sortedBy { it.id.value }.map { it.measurement },
        )
        val movement = stashRepository.allMovements().single()
        assertEquals(1L, movement.itemId.value)
        assertEquals(
            StashMovementOperation.IngredientSubtract,
            movement.operation,
        )
        assertEquals(
            StashMeasurement.servings(1.0),
            movement.measurementChange.negate(),
        )
    }

    @Test
    fun when_partial_logging_recipe_with_partial_direct_sub_recipe_match_then_product_entries_are_not_subtracted_as_fallback() = runBlocking {
        val flour = sampleProduct(id = 1, name = "Flour", brand = null)
        val dough =
            Recipe(
                id = FoodId.Recipe(9),
                name = "Prepared dough",
                servings = 2,
                ingredients = listOf(RecipeIngredient(flour, Measurement.Gram(200.0))),
                note = null,
                isLiquid = false,
            )
        val parentRecipe =
            Recipe(
                id = FoodId.Recipe(10),
                name = "Pizza",
                servings = 2,
                ingredients = listOf(RecipeIngredient(dough, Measurement.Serving(1.0))),
                note = null,
                isLiquid = false,
            )
        val stashRepository =
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
            )
        val useCase =
            LogRecipeToMealWithStashSubtractionUseCase(
                recipeRepository = FakeRecipeRepository(listOf(parentRecipe, dough)),
                entryRepository = FakeFoodDiaryEntryRepository(),
                mealRepository = FakeMealRepository(listOf(sampleMeal())),
                stashRepository = stashRepository,
                stashOwnerProvider = localOwnerProvider(),
                transactionProvider = FakeTransactionProvider(),
                dateProvider = FixedDateProvider(),
                logger = NoOpLogger,
            )

        val result =
            useCase.log(
                recipeId = parentRecipe.id,
                measurement = Measurement.Serving(2.0),
                mealId = sampleMeal().id,
                date = FIXED_NOW.date,
                subtractMode = StashSubtractionMode.Partial,
            )

        val success =
            assertIs<
                Success<LogRecipeToMealWithStashSubtractionResult, LogRecipeToMealWithStashSubtractionError>
            >(result)
        assertEquals(false, success.data.availability.areAllIngredientsAvailable)
        assertEquals(
            listOf(
                StashMeasurement.servings(0.0),
                StashMeasurement.grams(250.0),
            ),
            stashRepository.allItems().sortedBy { it.id.value }.map { it.measurement },
        )
        val movement = stashRepository.allMovements().single()
        assertEquals(1L, movement.itemId.value)
        assertEquals(
            StashMeasurement.servings(0.5),
            movement.measurementChange.negate(),
        )
    }

    @Test
    fun when_direct_sub_recipe_stash_entry_uses_servings_then_weight_requirement_subtracts_recipe_entry() = runBlocking {
        val flour = sampleProduct(id = 1, name = "Flour", brand = null)
        val dough =
            Recipe(
                id = FoodId.Recipe(9),
                name = "Prepared dough",
                servings = 2,
                ingredients = listOf(RecipeIngredient(flour, Measurement.Gram(400.0))),
                note = null,
                isLiquid = false,
            )
        val parentRecipe =
            Recipe(
                id = FoodId.Recipe(10),
                name = "Pizza",
                servings = 2,
                ingredients = listOf(RecipeIngredient(dough, Measurement.Gram(200.0))),
                note = null,
                isLiquid = false,
            )
        val stashRepository =
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
            )
        val diaryRepository = FakeFoodDiaryEntryRepository()
        val useCase =
            LogRecipeToMealWithStashSubtractionUseCase(
                recipeRepository = FakeRecipeRepository(listOf(parentRecipe, dough)),
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
                recipeId = parentRecipe.id,
                measurement = Measurement.Serving(2.0),
                mealId = sampleMeal().id,
                date = FIXED_NOW.date,
                subtractMode = StashSubtractionMode.Auto,
            )

        val success =
            assertIs<
                Success<LogRecipeToMealWithStashSubtractionResult, LogRecipeToMealWithStashSubtractionError>
            >(result)
        assertEquals(true, success.data.availability.areAllIngredientsAvailable)
        assertEquals(
            listOf(
                StashMeasurement.servings(0.0),
                StashMeasurement.grams(500.0),
            ),
            stashRepository.allItems().sortedBy { it.id.value }.map { it.measurement },
        )
        val movement = stashRepository.allMovements().single()
        assertEquals(1L, movement.itemId.value)
        assertEquals(StashMeasurement.servings(1.0), movement.measurementChange.negate())
        assertEquals("Pizza", diaryRepository.allEntries().single().food.name)
    }
}
