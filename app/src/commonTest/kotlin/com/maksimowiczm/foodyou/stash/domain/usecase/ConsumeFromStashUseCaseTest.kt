package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.result.Result.Error
import com.maksimowiczm.foodyou.common.result.Result.Success
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

class ConsumeFromStashUseCaseTest {
    @Test
    fun when_consuming_raw_product_then_diary_entry_is_created_and_remaining_quantity_is_updated() = runBlocking {
        val stashRepository =
            FakeStashRepository(
                initialStashes = listOf(sampleStash()),
                initialItems = listOf(sampleRawProductItem(measurement = StashMeasurement.grams(500.0))),
            )
        val diaryRepository = FakeFoodDiaryEntryRepository()
        val useCase =
            ConsumeFromStashUseCase(
                stashRepository = stashRepository,
                entryRepository = diaryRepository,
                mealRepository = FakeMealRepository(listOf(sampleMeal())),
                transactionProvider = FakeTransactionProvider(),
                dateProvider = FixedDateProvider(),
                logger = NoOpLogger,
            )

        val result =
            useCase.consume(
                itemId = sampleRawProductItem().id,
                mealId = sampleMeal().id,
                amountEaten = StashMeasurement.grams(200.0),
            )

        val success =
            assertIs<Success<com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId, ConsumeFromStashError>>(result)
        assertEquals(1L, success.data.value)
        assertEquals(StashMeasurement.grams(300.0), stashRepository.allItems().single().measurement)
        assertEquals(
            Measurement.Gram(200.0),
            diaryRepository.allEntries().single().measurement,
        )
        assertEquals(StashMovementOperation.DirectConsume, stashRepository.allMovements().single().operation)
        assertEquals(StashMeasurement.grams(-200.0), stashRepository.allMovements().single().measurementChange)
    }

    @Test
    fun when_consuming_dish_by_weight_equivalent_then_fraction_quantity_is_subtracted() = runBlocking {
        // 3-serving dish, totalWeight = 450g (150g per serving), 2 servings available in stash
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
                        )
                    ),
            )
        val diaryRepository = FakeFoodDiaryEntryRepository()
        val useCase =
            ConsumeFromStashUseCase(
                stashRepository = stashRepository,
                entryRepository = diaryRepository,
                mealRepository = FakeMealRepository(listOf(sampleMeal())),
                transactionProvider = FakeTransactionProvider(),
                dateProvider = FixedDateProvider(),
                logger = NoOpLogger,
            )

        // Eating 150g from a 3-serving dish (450g total).
        // servingsConsumed = 150 / 450 * 3.0 = 1.0
        // remaining = 2.0 - 1.0 = 1.0 servings
        val result =
            useCase.consume(
                itemId = sampleAnonymousDishItem().id,
                mealId = sampleMeal().id,
                amountEaten = StashMeasurement.grams(150.0),
            )

        val success =
            assertIs<Success<com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId, ConsumeFromStashError>>(result)
        assertEquals(1L, success.data.value)
        assertEquals(StashMeasurement.servings(1.0), stashRepository.allItems().single().measurement)
        assertEquals(
            Measurement.Gram(150.0),
            diaryRepository.allEntries().single().measurement,
        )
        assertEquals(StashMeasurement.servings(-1.0), stashRepository.allMovements().single().measurementChange)
    }

    @Test
    fun when_consumed_amount_exceeds_available_quantity_then_it_returns_error() = runBlocking {
        val useCase =
            ConsumeFromStashUseCase(
                stashRepository =
                    FakeStashRepository(
                        initialStashes = listOf(sampleStash()),
                        initialItems = listOf(sampleRawProductItem(measurement = StashMeasurement.grams(100.0))),
                    ),
                entryRepository = FakeFoodDiaryEntryRepository(),
                mealRepository = FakeMealRepository(listOf(sampleMeal())),
                transactionProvider = FakeTransactionProvider(),
                dateProvider = FixedDateProvider(),
                logger = NoOpLogger,
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
    fun when_consuming_exactly_all_available_raw_product_quantity_then_item_is_preserved_for_metadata_restore() = runBlocking {
        val stashRepository =
            FakeStashRepository(
                initialStashes = listOf(sampleStash()),
                initialItems = listOf(sampleRawProductItem(measurement = StashMeasurement.grams(100.0))),
            )
        val useCase =
            ConsumeFromStashUseCase(
                stashRepository = stashRepository,
                entryRepository = FakeFoodDiaryEntryRepository(),
                mealRepository = FakeMealRepository(listOf(sampleMeal())),
                transactionProvider = FakeTransactionProvider(),
                dateProvider = FixedDateProvider(),
                logger = NoOpLogger,
            )

        val result =
            useCase.consume(
                itemId = sampleRawProductItem().id,
                mealId = sampleMeal().id,
                amountEaten = StashMeasurement.grams(100.0),
            )

        assertIs<Success<com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId, ConsumeFromStashError>>(result)
        // Raw product with productId != null → canDeleteWhenEmpty() returns false, so item is kept
        assertEquals(listOf(StashMeasurement.grams(0.0)), stashRepository.allItems().map { it.measurement })
    }

    @Test
    fun when_consumed_amount_exceeds_available_quantity_then_error_includes_requested_and_available_amounts() = runBlocking {
        val useCase =
            ConsumeFromStashUseCase(
                stashRepository =
                    FakeStashRepository(
                        initialStashes = listOf(sampleStash()),
                        initialItems = listOf(sampleRawProductItem(measurement = StashMeasurement.grams(50.0))),
                    ),
                entryRepository = FakeFoodDiaryEntryRepository(),
                mealRepository = FakeMealRepository(listOf(sampleMeal())),
                transactionProvider = FakeTransactionProvider(),
                dateProvider = FixedDateProvider(),
                logger = NoOpLogger,
            )

        val result =
            useCase.consume(
                itemId = sampleRawProductItem().id,
                mealId = sampleMeal().id,
                amountEaten = StashMeasurement.grams(100.0),
            )

        assertEquals(
            ConsumeFromStashError.InsufficientQuantity(
                available = StashMeasurement.grams(50.0),
                requested = StashMeasurement.grams(100.0),
            ),
            assertIs<Error<com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId, ConsumeFromStashError>>(result).error,
        )
    }

    @Test
    fun when_recording_consumption_fails_then_stash_and_diary_changes_are_rolled_back() = runBlocking {
        val stashRepository =
            FakeStashRepository(
                initialStashes = listOf(sampleStash()),
                initialItems = listOf(sampleRawProductItem(measurement = StashMeasurement.grams(500.0))),
                failOnMovementInsertAttempt = 1,
            )
        val diaryRepository = FakeFoodDiaryEntryRepository()
        val useCase =
            ConsumeFromStashUseCase(
                stashRepository = stashRepository,
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
}