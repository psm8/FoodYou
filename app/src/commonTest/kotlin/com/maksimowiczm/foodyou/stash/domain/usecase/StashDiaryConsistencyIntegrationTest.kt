package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodProduct
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntry
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId
import com.maksimowiczm.foodyou.fooddiary.domain.usecase.UpdateFoodDiaryEntryUseCase
import com.maksimowiczm.foodyou.stash.domain.entity.LinkedDiaryEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementId
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking

class StashDiaryConsistencyIntegrationTest {
    @Test
    fun when_add_consume_and_edit_happen_rapidly_then_stash_state_stays_consistent() = runBlocking {
        val product = sampleProduct()
        val originalItem = sampleRawProductItem(id = 1, quantity = StashQuantity.grams(500.0), product = product)
        val linkedEntry =
            FoodDiaryEntry(
                id = FoodDiaryEntryId(1),
                mealId = sampleMeal().id,
                date = FIXED_NOW.date,
                measurement = Measurement.Gram(120.0),
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
        val rebalancedItem = sampleRawProductItem(id = 2, quantity = StashQuantity.grams(0.0), product = product)
        val stashRepository =
            FakeStashRepository(
                initialStashes = listOf(sampleStash()),
                initialItems = listOf(originalItem, rebalancedItem),
                initialMovements =
                    listOf(
                        StashMovement(
                            id = StashMovementId(1),
                            stashId = rebalancedItem.stashId,
                            itemId = rebalancedItem.id,
                            operation = StashMovementOperation.DirectConsume,
                            quantityChange = StashQuantity.grams(-120.0),
                            linkedDiaryEntryId = LinkedDiaryEntryId(linkedEntry.id.value),
                            createdAt = FIXED_NOW,
                        )
                    ),
            )
        val diaryRepository = FakeFoodDiaryEntryRepository(initialEntries = listOf(linkedEntry))
        val transactionProvider = SnapshottingFakeTransactionProvider(stashRepository, diaryRepository)
        val addUseCase =
            AddProductToStashUseCase(
                productRepository = FakeProductRepository(listOf(product)),
                stashRepository = stashRepository,
                stashOwnerProvider = localOwnerProvider(),
                transactionProvider = transactionProvider,
                dateProvider = FixedDateProvider(),
                logger = NoOpLogger,
            )
        val consumeUseCase =
            ConsumeFromStashUseCase(
                stashRepository = stashRepository,
                entryRepository = diaryRepository,
                mealRepository = FakeMealRepository(listOf(sampleMeal())),
                transactionProvider = transactionProvider,
                dateProvider = FixedDateProvider(),
                logger = NoOpLogger,
            )
        val updateUseCase =
            UpdateFoodDiaryEntryUseCase(
                mealRepository = FakeMealRepository(listOf(sampleMeal())),
                entryRepository = diaryRepository,
                restoreLinkedDiaryEntryStashUseCase =
                    RestoreLinkedDiaryEntryStashUseCase(
                        stashRepository = stashRepository,
                        dateProvider = FixedDateProvider(),
                        logger = NoOpLogger,
                    ),
                dateProvider = FixedDateProvider(),
                transactionProvider = transactionProvider,
                logger = NoOpLogger,
            )

        coroutineScope {
            awaitAll(
                async {
                    addUseCase.add(
                        productId = FoodId.Product(product.id.id),
                        quantity = StashQuantity.grams(200.0),
                    )
                },
                async {
                    consumeUseCase.consume(
                        itemId = originalItem.id,
                        mealId = sampleMeal().id,
                        amountEaten = StashQuantity.grams(150.0),
                    )
                },
                async {
                    updateUseCase.update(
                        id = linkedEntry.id,
                        measurement = Measurement.Gram(60.0),
                        mealId = sampleMeal().id,
                        date = FIXED_NOW.date,
                    )
                },
            )
        }

        assertEquals(
            listOf(350.0, 60.0, 200.0),
            stashRepository.allItems().sortedBy { it.id.value }.map { it.quantity.amount },
        )
        assertEquals(
            listOf(60.0, 150.0),
            diaryRepository.allEntries().sortedBy { it.id.value }.map { (it.measurement as Measurement.Gram).value },
        )
    }
}
