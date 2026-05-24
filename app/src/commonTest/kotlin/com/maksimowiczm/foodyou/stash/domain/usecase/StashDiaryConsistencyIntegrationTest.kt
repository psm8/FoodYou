package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.food.NutrientValue.Companion.toNutrientValue
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.common.result.Result.Success
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodProduct
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntry
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId
import com.maksimowiczm.foodyou.fooddiary.domain.usecase.UpdateFoodDiaryEntryUseCase
import com.maksimowiczm.foodyou.stash.domain.entity.LinkedDiaryEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.StashFoodRef
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementId
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking

class StashDiaryConsistencyIntegrationTest {
    @Test
    fun when_manual_quick_add_is_requested_then_it_creates_catalog_backed_stash_entry() = runBlocking {
        val stash = sampleStash()
        val stashRepository = FakeStashRepository(initialStashes = listOf(stash))
        val productRepository = FakeProductRepository()
        val diaryRepository = FakeFoodDiaryEntryRepository()
        val useCase =
            CreateManualStashSnapshotUseCase(
                productRepository = productRepository,
                stashRepository = stashRepository,
                stashOwnerProvider = localOwnerProvider(),
                transactionProvider =
                    SnapshottingFakeTransactionProvider(stashRepository, productRepository, diaryRepository),
                dateProvider = FixedDateProvider(),
                logger = NoOpLogger,
            )

        val result =
            useCase.create(
                name = "Quick shake",
                nutritionFacts =
                    NutritionFacts(
                        energy = 240.0.toNutrientValue(),
                        proteins = 30.0.toNutrientValue(),
                        carbohydrates = 12.0.toNutrientValue(),
                        fats = 6.0.toNutrientValue(),
                    ),
                measurement = Measurement.Milliliter(300.0),
                stashId = stash.id,
            )

        val success =
            assertIs<Success<CreateManualStashSnapshotResult, CreateManualStashSnapshotError>>(result)
        assertEquals(stash.id, success.data.stashId)
        assertEquals(StashFoodRef.Product(productRepository.allProducts().single().id), stashRepository.allItems().single().foodRef)
        assertEquals(emptyList(), diaryRepository.allEntries())
        assertEquals(1, stashRepository.allItems().size)
        assertEquals(1, stashRepository.allMovements().size)
    }

    @Test
    fun when_same_product_is_added_twice_with_same_measurement_type_then_item_quantity_is_merged_and_movements_stay_separate() =
        runBlocking {
            val product = sampleProduct(servingWeight = 100.0)
            val stashRepository = FakeStashRepository(initialStashes = listOf(sampleStash()))
            val transactionProvider =
                SnapshottingFakeTransactionProvider(stashRepository, FakeFoodDiaryEntryRepository())
            val useCase =
                AddProductToStashUseCase(
                    productRepository = FakeProductRepository(listOf(product)),
                    stashRepository = stashRepository,
                    stashOwnerProvider = localOwnerProvider(),
                    transactionProvider = transactionProvider,
                    dateProvider = FixedDateProvider(),
                    logger = NoOpLogger,
                )

            val first = useCase.add(productId = product.id, measurement = Measurement.Gram(100.0))
            val second = useCase.add(productId = product.id, measurement = Measurement.Gram(100.0))

            assertIs<Success<*, *>>(first)
            assertIs<Success<*, *>>(second)
            assertEquals(1, stashRepository.allItems().size)
            assertEquals(StashFoodRef.Product(product.id), stashRepository.allItems().single().foodRef)
            assertEquals(StashMeasurement.grams(200.0), stashRepository.allItems().single().measurement)
            assertEquals(
                listOf(StashMeasurement.grams(100.0), StashMeasurement.grams(100.0)),
                stashRepository.allMovements().map { it.measurementChange },
            )
        }

    @Test
    fun when_add_consume_and_edit_happen_rapidly_then_stash_state_stays_consistent() = runBlocking {
        val product = sampleProduct()
        val originalItem =
            sampleRawProductItem(id = 1, measurement = StashMeasurement.grams(500.0), product = product)
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
        val rebalancedItem =
            sampleRawProductItem(id = 2, measurement = StashMeasurement.grams(0.0), product = product)
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
                            measurementChange = StashMeasurement.grams(-120.0),
                            linkedDiaryEntryId = LinkedDiaryEntryId(linkedEntry.id.value),
                            createdAt = FIXED_NOW,
                        ),
                    ),
            )
        val diaryRepository = FakeFoodDiaryEntryRepository(initialEntries = listOf(linkedEntry))
        val transactionProvider =
            SnapshottingFakeTransactionProvider(stashRepository, diaryRepository)
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
                productRepository = FakeProductRepository(listOf(product)),
                recipeRepository = FakeRecipeRepository(),
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
                        productRepository = FakeProductRepository(listOf(product)),
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
                        measurement = Measurement.Gram(200.0),
                    )
                },
                async {
                    consumeUseCase.consume(
                        itemId = originalItem.id,
                        mealId = sampleMeal().id,
                        amountEaten = StashMeasurement.grams(150.0),
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
            listOf(550.0, 60.0),
            stashRepository.allItems().sortedBy { it.id.value }.map { it.measurement.measurement.rawValue },
        )
        assertEquals(
            listOf(60.0, 150.0),
            diaryRepository
                .allEntries()
                .sortedBy { it.id.value }
                .map { (it.measurement as Measurement.Gram).value },
        )
    }
}
