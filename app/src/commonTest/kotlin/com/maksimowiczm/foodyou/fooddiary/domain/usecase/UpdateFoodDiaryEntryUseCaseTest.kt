package com.maksimowiczm.foodyou.fooddiary.domain.usecase

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.result.Result.Success
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodProduct
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntry
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.LinkedDiaryEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementId
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
import com.maksimowiczm.foodyou.stash.domain.usecase.FIXED_NOW
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeFoodDiaryEntryRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeMealRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeStashRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeTransactionProvider
import com.maksimowiczm.foodyou.stash.domain.usecase.FixedDateProvider
import com.maksimowiczm.foodyou.stash.domain.usecase.NoOpLogger
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleMeal
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleProduct
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleRawProductItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

class UpdateFoodDiaryEntryUseCaseTest {
    @Test
    fun when_entry_is_edited_down_then_linked_stash_quantity_is_restored() = runBlocking {
        val product = sampleProduct()
        val item = sampleRawProductItem(quantity = StashQuantity.grams(0.0), product = product)
        val entry = sampleFoodDiaryEntry(product = product, measurement = Measurement.Gram(200.0))
        val stashRepository =
            FakeStashRepository(
                initialStashes = listOf(com.maksimowiczm.foodyou.stash.domain.usecase.sampleStash()),
                initialItems = listOf(item),
                initialMovements =
                    listOf(
                        StashMovement(
                            id = StashMovementId(1),
                            stashId = item.stashId,
                            itemId = item.id,
                            operation = StashMovementOperation.DirectConsume,
                            quantityChange = StashQuantity.grams(-200.0),
                            linkedDiaryEntryId = LinkedDiaryEntryId(entry.id.value),
                            createdAt = FIXED_NOW,
                        )
                    ),
            )
        val entryRepository = FakeFoodDiaryEntryRepository(initialEntries = listOf(entry))
        val useCase = createUseCase(stashRepository = stashRepository, entryRepository = entryRepository)

        val result =
            useCase.update(
                id = entry.id,
                measurement = Measurement.Gram(150.0),
                mealId = sampleMeal().id,
                date = FIXED_NOW.date,
            )

        assertIs<Success<Unit, UpdateFoodDiaryEntryError>>(result)
        assertEquals(Measurement.Gram(150.0), entryRepository.allEntries().single().measurement)
        assertEquals(StashQuantity.grams(50.0), stashRepository.allItems().single().quantity)
        assertEquals(
            listOf(
                StashQuantity.grams(-200.0),
                StashQuantity.grams(50.0),
            ),
            stashRepository.allMovements().map { it.quantityChange },
        )
        assertEquals(
            StashMovementOperation.AutoReversalOnEdit,
            stashRepository.allMovements().last().operation,
        )
    }

    @Test
    fun when_entry_is_edited_up_then_additional_stash_quantity_is_consumed() = runBlocking {
        val product = sampleProduct()
        val item = sampleRawProductItem(quantity = StashQuantity.grams(50.0), product = product)
        val entry = sampleFoodDiaryEntry(product = product, measurement = Measurement.Gram(150.0))
        val stashRepository =
            FakeStashRepository(
                initialStashes = listOf(com.maksimowiczm.foodyou.stash.domain.usecase.sampleStash()),
                initialItems = listOf(item),
                initialMovements =
                    listOf(
                        StashMovement(
                            id = StashMovementId(1),
                            stashId = item.stashId,
                            itemId = item.id,
                            operation = StashMovementOperation.DirectConsume,
                            quantityChange = StashQuantity.grams(-150.0),
                            linkedDiaryEntryId = LinkedDiaryEntryId(entry.id.value),
                            createdAt = FIXED_NOW,
                        )
                    ),
            )
        val entryRepository = FakeFoodDiaryEntryRepository(initialEntries = listOf(entry))
        val useCase = createUseCase(stashRepository = stashRepository, entryRepository = entryRepository)

        val result =
            useCase.update(
                id = entry.id,
                measurement = Measurement.Gram(200.0),
                mealId = sampleMeal().id,
                date = FIXED_NOW.date,
            )

        assertIs<Success<Unit, UpdateFoodDiaryEntryError>>(result)
        assertEquals(Measurement.Gram(200.0), entryRepository.allEntries().single().measurement)
        assertEquals(StashQuantity.grams(0.0), stashRepository.allItems().single().quantity)
        assertEquals(
            listOf(
                StashQuantity.grams(-150.0),
                StashQuantity.grams(-50.0),
            ),
            stashRepository.allMovements().map { it.quantityChange },
        )
        assertEquals(
            StashMovementOperation.AutoReversalOnEdit,
            stashRepository.allMovements().last().operation,
        )
    }

    @Test
    fun when_consumed_item_was_deleted_after_reaching_zero_then_editing_down_recreates_it() = runBlocking {
        val product = sampleProduct()
        val entry = sampleFoodDiaryEntry(product = product, measurement = Measurement.Gram(200.0))
        val stashRepository =
            FakeStashRepository(
                initialStashes = listOf(com.maksimowiczm.foodyou.stash.domain.usecase.sampleStash()),
                initialMovements =
                    listOf(
                        StashMovement(
                            id = StashMovementId(1),
                            stashId = com.maksimowiczm.foodyou.stash.domain.usecase.sampleStash().id,
                            itemId = com.maksimowiczm.foodyou.stash.domain.usecase.sampleRawProductItem().id,
                            operation = StashMovementOperation.DirectConsume,
                            quantityChange = StashQuantity.grams(-200.0),
                            linkedDiaryEntryId = LinkedDiaryEntryId(entry.id.value),
                            createdAt = FIXED_NOW,
                        )
                    ),
            )
        val entryRepository = FakeFoodDiaryEntryRepository(initialEntries = listOf(entry))
        val useCase = createUseCase(stashRepository = stashRepository, entryRepository = entryRepository)

        val result =
            useCase.update(
                id = entry.id,
                measurement = Measurement.Gram(150.0),
                mealId = sampleMeal().id,
                date = FIXED_NOW.date,
            )

        assertIs<Success<Unit, UpdateFoodDiaryEntryError>>(result)
        assertEquals(listOf(StashQuantity.grams(50.0)), stashRepository.allItems().map { it.quantity })
        assertEquals(
            listOf(
                StashQuantity.grams(-200.0),
                StashQuantity.grams(50.0),
            ),
            stashRepository.allMovements().map { it.quantityChange },
        )
    }

    @Test
    fun when_entry_is_repeatedly_edited_down_then_only_incremental_quantity_is_restored() = runBlocking {
        val product = sampleProduct()
        val item = sampleRawProductItem(quantity = StashQuantity.grams(0.0), product = product)
        val entry = sampleFoodDiaryEntry(product = product, measurement = Measurement.Gram(200.0))
        val stashRepository =
            FakeStashRepository(
                initialStashes = listOf(com.maksimowiczm.foodyou.stash.domain.usecase.sampleStash()),
                initialItems = listOf(item),
                initialMovements =
                    listOf(
                        StashMovement(
                            id = StashMovementId(1),
                            stashId = item.stashId,
                            itemId = item.id,
                            operation = StashMovementOperation.DirectConsume,
                            quantityChange = StashQuantity.grams(-200.0),
                            linkedDiaryEntryId = LinkedDiaryEntryId(entry.id.value),
                            createdAt = FIXED_NOW,
                        )
                    ),
            )
        val entryRepository = FakeFoodDiaryEntryRepository(initialEntries = listOf(entry))
        val useCase = createUseCase(stashRepository = stashRepository, entryRepository = entryRepository)

        val firstUpdate =
            useCase.update(
                id = entry.id,
                measurement = Measurement.Gram(150.0),
                mealId = sampleMeal().id,
                date = FIXED_NOW.date,
            )
        val secondUpdate =
            useCase.update(
                id = entry.id,
                measurement = Measurement.Gram(100.0),
                mealId = sampleMeal().id,
                date = FIXED_NOW.date,
            )

        assertIs<Success<Unit, UpdateFoodDiaryEntryError>>(firstUpdate)
        assertIs<Success<Unit, UpdateFoodDiaryEntryError>>(secondUpdate)
        assertEquals(Measurement.Gram(100.0), entryRepository.allEntries().single().measurement)
        assertEquals(StashQuantity.grams(100.0), stashRepository.allItems().single().quantity)
        assertEquals(
            listOf(
                StashQuantity.grams(-200.0),
                StashQuantity.grams(50.0),
                StashQuantity.grams(50.0),
            ),
            stashRepository.allMovements().map { it.quantityChange },
        )
    }

    private fun createUseCase(
        stashRepository: FakeStashRepository,
        entryRepository: FakeFoodDiaryEntryRepository,
    ) = UpdateFoodDiaryEntryUseCase(
        mealRepository = FakeMealRepository(listOf(sampleMeal())),
        entryRepository = entryRepository,
        restoreLinkedDiaryEntryStashUseCase =
            com.maksimowiczm.foodyou.stash.domain.usecase.RestoreLinkedDiaryEntryStashUseCase(
                stashRepository = stashRepository,
                dateProvider = FixedDateProvider(),
                logger = NoOpLogger,
            ),
        dateProvider = FixedDateProvider(),
        transactionProvider = FakeTransactionProvider(),
        logger = NoOpLogger,
    )

    private fun sampleFoodDiaryEntry(
        product: com.maksimowiczm.foodyou.food.domain.entity.Product,
        measurement: Measurement,
    ): FoodDiaryEntry =
        FoodDiaryEntry(
            id = FoodDiaryEntryId(1),
            mealId = sampleMeal().id,
            date = FIXED_NOW.date,
            measurement = measurement,
            food =
                DiaryFoodProduct(
                    name = product.headline,
                    nutritionFacts = product.nutritionFacts,
                    servingWeight = product.servingWeight,
                    totalWeight = product.totalWeight,
                    isLiquid = product.isLiquid,
                    source = product.source,
                    note = product.note,
                ),
            createdAt = FIXED_NOW,
            updatedAt = FIXED_NOW,
        )
}
