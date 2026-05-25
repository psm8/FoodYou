package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.result.Result.Error
import com.maksimowiczm.foodyou.common.result.Result.Success
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodRecipe
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

class ConsumeFromStashUseCaseTest {
    @Test
    fun when_consuming_raw_product_then_diary_entry_is_created_and_remaining_quantity_is_updated() = runBlocking {
        val product = sampleProduct()
        val stashRepository =
            FakeStashRepository(
                initialStashes = listOf(sampleStash()),
                initialItems = listOf(sampleRawProductItem(measurement = StashMeasurement.grams(500.0), product = product)),
            )
        val diaryRepository = FakeFoodDiaryEntryRepository()
        val useCase = createUseCase(stashRepository, diaryRepository, products = listOf(product))

        val result =
            useCase.consume(
                itemId = sampleRawProductItem().id,
                mealId = sampleMeal().id,
                amountEaten = StashMeasurement.grams(200.0),
            )

        val success = assertIs<Success<com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId, ConsumeFromStashError>>(result)
        assertEquals(1L, success.data.value)
        assertEquals(StashMeasurement.grams(300.0), stashRepository.allItems().single().measurement)
        assertEquals(Measurement.Gram(200.0), diaryRepository.allEntries().single().measurement)
        assertEquals(StashMovementOperation.DirectConsume, stashRepository.allMovements().single().operation)
        assertEquals(StashMeasurement.grams(-200.0), stashRepository.allMovements().single().measurementChange)
    }

    @Test
    fun when_consuming_recipe_by_weight_equivalent_then_fraction_quantity_is_subtracted_and_recipe_is_resolved_live() = runBlocking {
        val recipe = sampleRecipe(id = 1L, name = "Pasta", servings = 3, totalWeight = 450.0, isLiquid = false)
        val stashRepository =
            FakeStashRepository(
                initialStashes = listOf(sampleStash()),
                initialItems =
                    listOf(
                        sampleAnonymousDishItem(
                            measurement = StashMeasurement.servings(2.0),
                            recipe = recipe,
                            totalAmount = Measurement.Serving(3.0),
                            servingsMade = 3,
                        ),
                    ),
            )
        val diaryRepository = FakeFoodDiaryEntryRepository()
        val useCase = createUseCase(stashRepository, diaryRepository, recipes = listOf(recipe))

        val result =
            useCase.consume(
                itemId = sampleAnonymousDishItem().id,
                mealId = sampleMeal().id,
                amountEaten = StashMeasurement.grams(150.0),
            )

        val success = assertIs<Success<com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId, ConsumeFromStashError>>(result)
        assertEquals(1L, success.data.value)
        assertEquals(StashMeasurement.servings(1.0), stashRepository.allItems().single().measurement)
        assertEquals(Measurement.Gram(150.0), diaryRepository.allEntries().single().measurement)
        assertEquals("Pasta", diaryRepository.allEntries().single().food.name)
        assertIs<DiaryFoodRecipe>(diaryRepository.allEntries().single().food)
        assertEquals(StashMeasurement.servings(-1.0), stashRepository.allMovements().single().measurementChange)
    }

    @Test
    fun when_consumed_amount_exceeds_available_quantity_then_it_returns_error() = runBlocking {
        val useCase =
            createUseCase(
                stashRepository =
                    FakeStashRepository(
                        initialStashes = listOf(sampleStash()),
                        initialItems = listOf(sampleRawProductItem(measurement = StashMeasurement.grams(100.0))),
                    ),
                diaryRepository = FakeFoodDiaryEntryRepository(),
                products = listOf(sampleProduct()),
            )

        val result =
            useCase.consume(
                itemId = sampleRawProductItem().id,
                mealId = sampleMeal().id,
                amountEaten = StashMeasurement.grams(250.0),
            )

        assertEquals(
            ConsumeFromStashError.InsufficientQuantity(
                available = StashMeasurement.grams(100.0),
                requested = StashMeasurement.grams(250.0),
            ),
            assertIs<Error<com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId, ConsumeFromStashError>>(result).error,
        )
    }

    @Test
    fun when_consuming_exactly_all_available_quantity_then_zero_quantity_item_is_preserved_for_future_restores() = runBlocking {
        val product = sampleProduct()
        val stashRepository =
            FakeStashRepository(
                initialStashes = listOf(sampleStash()),
                initialItems = listOf(sampleRawProductItem(measurement = StashMeasurement.grams(100.0), product = product)),
            )
        val useCase = createUseCase(stashRepository, FakeFoodDiaryEntryRepository(), products = listOf(product))

        val result =
            useCase.consume(
                itemId = sampleRawProductItem().id,
                mealId = sampleMeal().id,
                amountEaten = StashMeasurement.grams(100.0),
            )

        assertIs<Success<com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId, ConsumeFromStashError>>(result)
        assertEquals(listOf(StashMeasurement.grams(0.0)), stashRepository.allItems().map { it.measurement })
    }

    @Test
    fun when_recording_consumption_fails_then_stash_and_diary_changes_are_rolled_back() = runBlocking {
        val product = sampleProduct()
        val stashRepository =
            FakeStashRepository(
                initialStashes = listOf(sampleStash()),
                initialItems = listOf(sampleRawProductItem(measurement = StashMeasurement.grams(500.0), product = product)),
                failOnMovementInsertAttempt = 1,
            )
        val diaryRepository = FakeFoodDiaryEntryRepository()
        val useCase =
            ConsumeFromStashUseCase(
                stashRepository = stashRepository,
                productRepository = FakeProductRepository(listOf(product)),
                recipeRepository = FakeRecipeRepository(),
                entryRepository = diaryRepository,
                mealRepository = FakeMealRepository(listOf(sampleMeal())),
                transactionProvider = SnapshottingFakeTransactionProvider(stashRepository, diaryRepository),
                dateProvider = FixedDateProvider(),
                logger = NoOpLogger,
            )

        kotlin.test.assertFails {
            useCase.consume(
                itemId = sampleRawProductItem().id,
                mealId = sampleMeal().id,
                amountEaten = StashMeasurement.grams(200.0),
            )
        }

        assertEquals(listOf(StashMeasurement.grams(500.0)), stashRepository.allItems().map { it.measurement })
        assertEquals(emptyList(), diaryRepository.allEntries())
    }

    private fun createUseCase(
        stashRepository: FakeStashRepository,
        diaryRepository: FakeFoodDiaryEntryRepository,
        products: List<com.maksimowiczm.foodyou.food.domain.entity.Product> = emptyList(),
        recipes: List<com.maksimowiczm.foodyou.food.domain.entity.Recipe> = emptyList(),
    ) = ConsumeFromStashUseCase(
        stashRepository = stashRepository,
        productRepository = FakeProductRepository(products),
        recipeRepository = FakeRecipeRepository(recipes),
        entryRepository = diaryRepository,
        mealRepository = FakeMealRepository(listOf(sampleMeal())),
        transactionProvider = FakeTransactionProvider(),
        dateProvider = FixedDateProvider(),
        logger = NoOpLogger,
    )
}
