package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.result.Result.Error
import com.maksimowiczm.foodyou.common.result.Result.Success
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
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
                initialItems = listOf(sampleRawProductItem(quantity = StashQuantity.grams(500.0))),
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
                amountEaten = StashQuantity.grams(200.0),
            )

        val success =
            assertIs<Success<com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId, ConsumeFromStashError>>(result)
        assertEquals(1L, success.data.value)
        assertEquals(StashQuantity.grams(300.0), stashRepository.allItems().single().quantity)
        assertEquals(
            Measurement.Gram(200.0),
            diaryRepository.allEntries().single().measurement,
        )
        assertEquals(StashMovementOperation.DirectConsume, stashRepository.allMovements().single().operation)
        assertEquals(StashQuantity.grams(-200.0), stashRepository.allMovements().single().quantityChange)
    }

    @Test
    fun when_consuming_dish_by_weight_equivalent_then_fraction_quantity_is_subtracted() = runBlocking {
        val stashRepository =
            FakeStashRepository(
                initialStashes = listOf(sampleStash()),
                initialItems =
                    listOf(
                        sampleAnonymousDishItem(
                            quantity = StashQuantity.fraction(2.0),
                            totalAmount = StashQuantity.fraction(2.0),
                            servingsMade = 8,
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

        val result =
            useCase.consume(
                itemId = sampleAnonymousDishItem().id,
                mealId = sampleMeal().id,
                amountEaten = StashQuantity.grams(200.0),
            )

        val success =
            assertIs<Success<com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId, ConsumeFromStashError>>(result)
        assertEquals(1L, success.data.value)
        assertEquals(StashQuantity.fraction(1.5), stashRepository.allItems().single().quantity)
        assertEquals(
            Measurement.Gram(200.0),
            diaryRepository.allEntries().single().measurement,
        )
        assertEquals(StashQuantity.fraction(-0.5), stashRepository.allMovements().single().quantityChange)
    }

    @Test
    fun when_consumed_amount_exceeds_available_quantity_then_it_returns_error() = runBlocking {
        val useCase =
            ConsumeFromStashUseCase(
                stashRepository =
                    FakeStashRepository(
                        initialStashes = listOf(sampleStash()),
                        initialItems = listOf(sampleRawProductItem(quantity = StashQuantity.grams(100.0))),
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
                amountEaten = StashQuantity.grams(250.0),
            )

        assertEquals(
            ConsumeFromStashError.InsufficientQuantity(
                available = StashQuantity.grams(100.0),
                requested = StashQuantity.grams(250.0),
            ),
            assertIs<Error<com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId, ConsumeFromStashError>>(result).error,
        )
    }

    @Test
    fun when_consuming_exactly_all_available_raw_product_quantity_then_item_is_preserved_for_metadata_restore() = runBlocking {
        val stashRepository =
            FakeStashRepository(
                initialStashes = listOf(sampleStash()),
                initialItems = listOf(sampleRawProductItem(quantity = StashQuantity.grams(100.0))),
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
                amountEaten = StashQuantity.grams(100.0),
            )

        assertIs<Success<com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId, ConsumeFromStashError>>(result)
        assertEquals(listOf(StashQuantity.grams(0.0)), stashRepository.allItems().map { it.quantity })
    }

    @Test
    fun when_consumed_amount_exceeds_available_quantity_then_error_includes_requested_and_available_amounts() = runBlocking {
        val useCase =
            ConsumeFromStashUseCase(
                stashRepository =
                    FakeStashRepository(
                        initialStashes = listOf(sampleStash()),
                        initialItems = listOf(sampleRawProductItem(quantity = StashQuantity.grams(50.0))),
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
                amountEaten = StashQuantity.grams(100.0),
            )

        assertEquals(
            ConsumeFromStashError.InsufficientQuantity(
                available = StashQuantity.grams(50.0),
                requested = StashQuantity.grams(100.0),
            ),
            assertIs<Error<com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId, ConsumeFromStashError>>(result).error,
        )
    }

    @Test
    fun when_recording_consumption_fails_then_stash_and_diary_changes_are_rolled_back() = runBlocking {
        val stashRepository =
            FakeStashRepository(
                initialStashes = listOf(sampleStash()),
                initialItems = listOf(sampleRawProductItem(quantity = StashQuantity.grams(500.0))),
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
                amountEaten = StashQuantity.grams(200.0),
            )
        }

        assertEquals(listOf(StashQuantity.grams(500.0)), stashRepository.allItems().map { it.quantity })
        assertEquals(emptyList(), diaryRepository.allEntries())
    }
}
