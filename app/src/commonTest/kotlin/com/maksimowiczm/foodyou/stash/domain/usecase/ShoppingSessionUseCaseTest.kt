package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.result.Result.Success
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodProduct
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntry
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.ShoppingSession
import com.maksimowiczm.foodyou.stash.domain.entity.ShoppingSessionId
import com.maksimowiczm.foodyou.stash.domain.entity.ShoppingSessionItem
import com.maksimowiczm.foodyou.stash.domain.entity.ShoppingSessionItemId
import com.maksimowiczm.foodyou.stash.domain.entity.ShoppingSessionProductDetails
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.StashFoodRef
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

class ShoppingSessionUseCaseTest {
    @Test
    fun when_starting_session_for_existing_stash_then_it_creates_an_empty_temporary_session() = runBlocking {
        val stash = sampleStash(id = 7L, name = "Pantry")
        val startUseCase =
            StartShoppingSessionUseCase(
                stashRepository = FakeStashRepository(initialStashes = listOf(stash)),
                stashOwnerProvider = localOwnerProvider(),
                dateProvider = FixedDateProvider(),
                logger = NoOpLogger,
            )

        val session = assertSuccess(startUseCase.start(stash.id))

        assertEquals(stash.id, session.stashId)
        assertEquals(emptyList(), session.items)
        assertEquals(true, session.id.value.contains(stash.id.value.toString()))
    }

    @Test
    fun when_adding_product_to_session_then_session_accumulates_items_and_running_nutrition() = runBlocking {
        val startUseCase =
            StartShoppingSessionUseCase(
                stashRepository = FakeStashRepository(initialStashes = listOf(sampleStash())),
                stashOwnerProvider = localOwnerProvider(),
                dateProvider = FixedDateProvider(),
                logger = NoOpLogger,
            )
        val addUseCase =
            AddToShoppingSessionUseCase(
                productRepository =
                    FakeProductRepository(
                        listOf(
                            sampleProduct(
                                nutritionFacts = nutritionWithEnergy(100.0),
                            )
                        )
                    ),
                logger = NoOpLogger,
            )

        val started = assertSuccess(startUseCase.start(sampleStash().id))
        val updated =
            assertSuccess(
                addUseCase.add(
                    session = started,
                    productId = FoodId.Product(1),
                    measurement = Measurement.Gram(250.0),
                )
            )

        assertEquals(1, updated.items.size)
        assertEquals(250.0, updated.totalNutritionFacts.energy.value)
        assertEquals(FoodId.Product(1), updated.items.single().productId)
        assertEquals(StashFoodRef.Product(FoodId.Product(1)), updated.items.single().foodRef)
        assertEquals("Skyr (FoodYou)", updated.items.single().productDetails.name)
        assertEquals(StashMeasurement(Measurement.Gram(250.0)), updated.items.single().measurement)
    }

    @Test
    fun when_adding_same_product_multiple_times_then_session_merges_it_into_one_logical_item() = runBlocking {
        val stash = sampleStash(name = "Fridge")
        val startUseCase = shoppingSessionStarter(FakeStashRepository(initialStashes = listOf(stash)))
        val addUseCase =
            AddToShoppingSessionUseCase(
                productRepository =
                    FakeProductRepository(
                        listOf(sampleProduct(id = 1L, packageWeight = 1000.0, nutritionFacts = nutritionWithEnergy(100.0)))
                    ),
                logger = NoOpLogger,
            )

        val started = assertSuccess(startUseCase.start(stash.id))
        val withOnePackage =
            assertSuccess(
                addUseCase.add(
                    session = started,
                    productId = FoodId.Product(1L),
                    measurement = Measurement.Package(1.0),
                )
            )
        val merged =
            assertSuccess(
                addUseCase.add(
                    session = withOnePackage,
                    productId = FoodId.Product(1L),
                    measurement = Measurement.Package(0.5),
                )
            )

        assertEquals(1, merged.items.size)
        assertEquals(StashMeasurement(Measurement.Package(1.5)), merged.items.single().measurement)
        assertEquals(1500.0, merged.totalNutritionFacts.energy.value)
    }

    @Test
    fun when_confirming_a_session_with_merged_items_then_everything_is_added_to_stash_and_diary_stays_untouched() = runBlocking {
        val stash = sampleStash(name = "Fridge")
        val stashRepository = FakeStashRepository(initialStashes = listOf(stash))
        val productRepository =
            FakeProductRepository(
                listOf(
                    sampleProduct(id = 1L, name = "Skyr", nutritionFacts = nutritionWithEnergy(60.0)),
                    sampleProduct(id = 2L, name = "Rice", nutritionFacts = nutritionWithEnergy(130.0)),
                    sampleProduct(id = 3L, name = "Banana", nutritionFacts = nutritionWithEnergy(90.0)),
                    sampleProduct(id = 4L, name = "Peanut Butter", nutritionFacts = nutritionWithEnergy(588.0)),
                    sampleProduct(
                        id = 5L,
                        name = "Juice",
                        isLiquid = true,
                        nutritionFacts = nutritionWithEnergy(45.0),
                    ),
                )
            )
        val diaryRepository = FakeFoodDiaryEntryRepository(initialEntries = listOf(sampleDiaryEntry()))
        val startUseCase = shoppingSessionStarter(stashRepository)
        val addUseCase = AddToShoppingSessionUseCase(productRepository = productRepository, logger = NoOpLogger)
        val confirmUseCase = shoppingSessionConfirmer(stashRepository)

        val initialDiaryEntries = diaryRepository.allEntries()
        var session = assertSuccess(startUseCase.start(stash.id))
        session =
            assertSuccess(
                addUseCase.add(
                    session = session,
                    productId = FoodId.Product(1L),
                    measurement = Measurement.Package(1.0),
                )
            )
        session =
            assertSuccess(
                addUseCase.add(
                    session = session,
                    productId = FoodId.Product(2L),
                    measurement = Measurement.Gram(500.0),
                )
            )
        session =
            assertSuccess(
                addUseCase.add(
                    session = session,
                    productId = FoodId.Product(3L),
                    measurement = Measurement.Serving(1.0),
                )
            )
        session =
            assertSuccess(
                addUseCase.add(
                    session = session,
                    productId = FoodId.Product(4L),
                    measurement = Measurement.Gram(80.0),
                )
            )
        session =
            assertSuccess(
                addUseCase.add(
                    session = session,
                    productId = FoodId.Product(5L),
                    measurement = Measurement.Package(0.75),
                )
            )
        session =
            assertSuccess(
                addUseCase.add(
                    session = session,
                    productId = FoodId.Product(1L),
                    measurement = Measurement.Package(0.5),
                )
            )

        val insertedItemIds = assertSuccess(confirmUseCase.confirm(session))

        assertEquals(5, insertedItemIds.size)
        assertEquals(5, stashRepository.allItems().size)
        assertEquals(5, stashRepository.allMovements().size)
        assertEquals(
            listOf(
                StashFoodRef.Product(FoodId.Product(1L)),
                StashFoodRef.Product(FoodId.Product(2L)),
                StashFoodRef.Product(FoodId.Product(3L)),
                StashFoodRef.Product(FoodId.Product(4L)),
                StashFoodRef.Product(FoodId.Product(5L)),
            ),
            stashRepository.allItems().map { it.foodRef },
        )
        assertEquals(
            listOf(
                StashMeasurement(Measurement.Package(1.5)),
                StashMeasurement(Measurement.Gram(500.0)),
                StashMeasurement(Measurement.Serving(1.0)),
                StashMeasurement(Measurement.Gram(80.0)),
                StashMeasurement(Measurement.Package(0.75)),
            ),
            stashRepository.allItems().map { it.measurement },
        )
        assertEquals(
            List(5) { StashMovementOperation.Purchase },
            stashRepository.allMovements().map { it.operation },
        )
        assertEquals(insertedItemIds, stashRepository.allMovements().map { it.itemId })
        assertEquals(List(5) { null }, stashRepository.allMovements().map { it.linkedDiaryEntryId })
        assertEquals(initialDiaryEntries, diaryRepository.allEntries())
    }

    @Test
    fun when_confirming_fails_then_inserted_items_and_movements_are_rolled_back() = runBlocking {
        val stash = sampleStash()
        val stashRepository =
            FakeStashRepository(
                initialStashes = listOf(stash),
                failOnMovementInsertAttempt = 2,
            )
        val confirmUseCase =
            ConfirmShoppingSessionUseCase(
                stashRepository = stashRepository,
                stashOwnerProvider = localOwnerProvider(),
                transactionProvider = SnapshottingFakeTransactionProvider(stashRepository),
                dateProvider = FixedDateProvider(),
                logger = NoOpLogger,
            )
        val session =
            ShoppingSession(
                id = ShoppingSessionId("session-1"),
                stashId = stash.id,
                items =
                    listOf(
                        ShoppingSessionItem(
                            id = ShoppingSessionItemId("session-item-1"),
                            foodRef = StashFoodRef.Product(FoodId.Product(1)),
                            productDetails = ShoppingSessionProductDetails.from(sampleProduct()),
                            measurement = StashMeasurement.grams(200.0),
                        ),
                        ShoppingSessionItem(
                            id = ShoppingSessionItemId("session-item-2"),
                            foodRef = StashFoodRef.Product(FoodId.Product(2)),
                            productDetails =
                                ShoppingSessionProductDetails.from(
                                    sampleProduct(id = 2, name = "Yoghurt"),
                                ),
                            measurement = StashMeasurement.grams(150.0),
                        ),
                    ),
            )

        val failure =
            assertIs<com.maksimowiczm.foodyou.common.result.Result.Error<List<StashEntryId>, ConfirmShoppingSessionError>>(
                confirmUseCase.confirm(session)
            )

        assertEquals(ConfirmShoppingSessionError.Unknown, failure.error)
        assertEquals(emptyList(), stashRepository.allItems())
        assertEquals(emptyList(), stashRepository.allMovements())
    }

    private fun shoppingSessionStarter(stashRepository: FakeStashRepository): StartShoppingSessionUseCase =
        StartShoppingSessionUseCase(
            stashRepository = stashRepository,
            stashOwnerProvider = localOwnerProvider(),
            dateProvider = FixedDateProvider(),
            logger = NoOpLogger,
        )

    private fun shoppingSessionConfirmer(stashRepository: FakeStashRepository): ConfirmShoppingSessionUseCase =
        ConfirmShoppingSessionUseCase(
            stashRepository = stashRepository,
            stashOwnerProvider = localOwnerProvider(),
            transactionProvider = SnapshottingFakeTransactionProvider(stashRepository),
            dateProvider = FixedDateProvider(),
            logger = NoOpLogger,
        )

    private fun sampleDiaryEntry(): FoodDiaryEntry =
        FoodDiaryEntry(
            id = FoodDiaryEntryId(11L),
            mealId = 3L,
            date = FIXED_NOW.date,
            measurement = Measurement.Gram(150.0),
            food =
                DiaryFoodProduct(
                    name = "Existing entry",
                    nutritionFacts = nutritionWithEnergy(90.0),
                    servingWeight = 150.0,
                    totalWeight = 150.0,
                    isLiquid = false,
                    source = sampleProduct().source,
                    note = null,
                ),
            createdAt = FIXED_NOW,
            updatedAt = FIXED_NOW,
        )

    private fun <T, E> assertSuccess(result: com.maksimowiczm.foodyou.common.result.Result<T, E>): T =
        assertIs<Success<T, E>>(result).data
}
