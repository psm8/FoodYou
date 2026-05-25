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
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import com.maksimowiczm.foodyou.stash.domain.usecase.FIXED_NOW
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeFoodDiaryEntryRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeStashRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeTransactionProvider
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeProductRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.FixedDateProvider
import com.maksimowiczm.foodyou.stash.domain.usecase.NoOpLogger
import com.maksimowiczm.foodyou.stash.domain.usecase.RestoreLinkedDiaryEntryStashUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleProduct
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleRawProductItem
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleStash
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

class DeleteFoodDiaryEntryUseCaseTest {
    @Test
    fun when_entry_is_deleted_then_all_linked_stash_movements_are_reversed() = runBlocking {
        val product = sampleProduct()
        val consumedItem = sampleRawProductItem(id = 1, measurement = StashMeasurement.grams(0.0), product = product)
        val returnedItem = sampleRawProductItem(id = 2, measurement = StashMeasurement.grams(100.0), product = product)
        val entry = sampleFoodDiaryEntry(product = product, measurement = Measurement.Gram(400.0))
        val linkedDiaryEntryId = LinkedDiaryEntryId(entry.id.value)
        val stashRepository =
            FakeStashRepository(
                initialStashes = listOf(sampleStash()),
                initialItems = listOf(consumedItem, returnedItem),
                initialMovements =
                    listOf(
                        StashMovement(
                            id = StashMovementId(1),
                            stashId = consumedItem.stashId,
                            itemId = consumedItem.id,
                            operation = StashMovementOperation.DirectConsume,
                            measurementChange = StashMeasurement.grams(-500.0),
                            linkedDiaryEntryId = linkedDiaryEntryId,
                            createdAt = FIXED_NOW,
                        ),
                        StashMovement(
                            id = StashMovementId(2),
                            stashId = returnedItem.stashId,
                            itemId = returnedItem.id,
                            operation = StashMovementOperation.ReturnToStash,
                            measurementChange = StashMeasurement.grams(100.0),
                            linkedDiaryEntryId = linkedDiaryEntryId,
                            createdAt = FIXED_NOW,
                        ),
                    ),
            )
        val entryRepository = FakeFoodDiaryEntryRepository(initialEntries = listOf(entry))
        val useCase =
            DeleteFoodDiaryEntryUseCase(
                entryRepository = entryRepository,
                restoreLinkedDiaryEntryStashUseCase =
                    RestoreLinkedDiaryEntryStashUseCase(
                        stashRepository = stashRepository,
                        productRepository = FakeProductRepository(listOf(product)),
                        dateProvider = FixedDateProvider(),
                        logger = NoOpLogger,
                    ),
                transactionProvider = FakeTransactionProvider(),
                logger = NoOpLogger,
            )

        val result = useCase.delete(entry.id)

        assertIs<Success<Unit, DeleteFoodDiaryEntryError>>(result)
        assertEquals(emptyList(), entryRepository.allEntries())
        assertEquals(
            listOf(
                StashMeasurement.grams(500.0),
                StashMeasurement.grams(0.0),
            ),
            stashRepository.allItems().sortedBy { it.id.value }.map { it.measurement },
        )
        assertEquals(
            listOf(
                StashMeasurement.grams(-500.0),
                StashMeasurement.grams(100.0),
                StashMeasurement.grams(500.0),
                StashMeasurement.grams(-100.0),
            ),
            stashRepository.allMovements().map { it.measurementChange },
        )
        assertEquals(
            listOf(
                StashMovementOperation.DirectConsume,
                StashMovementOperation.ReturnToStash,
                StashMovementOperation.AutoReversalOnDelete,
                StashMovementOperation.AutoReversalOnDelete,
            ),
            stashRepository.allMovements().map { it.operation },
        )
    }

    private fun sampleFoodDiaryEntry(
        product: com.maksimowiczm.foodyou.food.domain.entity.Product,
        measurement: Measurement,
    ): FoodDiaryEntry =
        FoodDiaryEntry(
            id = FoodDiaryEntryId(1),
            mealId = com.maksimowiczm.foodyou.stash.domain.usecase.sampleMeal().id,
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
