package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.result.Result.Error
import com.maksimowiczm.foodyou.common.result.Result.Success
import com.maksimowiczm.foodyou.stash.domain.entity.RawProductSnapshot
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import com.maksimowiczm.foodyou.stash.domain.entity.StashName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

class AddProductToStashUseCaseTest {
    @Test
    fun when_first_product_is_added_then_default_stash_and_purchase_movement_are_created() =
        runBlocking {
            val product = sampleProduct()
            val stashRepository = FakeStashRepository()
            val useCase =
                AddProductToStashUseCase(
                    productRepository = FakeProductRepository(listOf(product)),
                    stashRepository = stashRepository,
                    stashOwnerProvider = localOwnerProvider(),
                    transactionProvider = FakeTransactionProvider(),
                    dateProvider = FixedDateProvider(),
                    logger = NoOpLogger,
                )

            val result = useCase.add(productId = product.id, measurement = Measurement.Gram(250.0))

            val success = assertIs<Success<AddProductToStashResult, AddProductToStashError>>(result)
            assertEquals(StashName.from("Stash"), stashRepository.allStashes().single().name)
            assertEquals(success.data.stashId, stashRepository.allStashes().single().id)
            assertEquals(
                RawProductSnapshot.from(product),
                stashRepository.allItems().single().snapshot,
            )
            assertEquals(
                StashMeasurement.grams(250.0),
                stashRepository.allItems().single().measurement,
            )
            assertEquals(
                StashMovementOperation.Purchase,
                stashRepository.allMovements().single().operation,
            )
            assertEquals(
                StashMeasurement.grams(250.0),
                stashRepository.allMovements().single().measurementChange,
            )
        }

    @Test
    fun when_measurement_is_invalid_for_product_then_it_returns_validation_error() = runBlocking {
        val useCase =
            AddProductToStashUseCase(
                productRepository = FakeProductRepository(listOf(sampleProduct(isLiquid = true))),
                stashRepository = FakeStashRepository(),
                stashOwnerProvider = localOwnerProvider(),
                transactionProvider = FakeTransactionProvider(),
                dateProvider = FixedDateProvider(),
                logger = NoOpLogger,
            )

        val result =
            useCase.add(
                productId = com.maksimowiczm.foodyou.food.domain.entity.FoodId.Product(1),
                measurement = Measurement.Gram(100.0),
            )

        assertEquals(
            AddProductToStashError.InvalidMeasurement,
            assertIs<Error<AddProductToStashResult, AddProductToStashError>>(result).error,
        )
    }

    @Test
    fun when_measurement_amount_is_zero_or_negative_then_it_returns_validation_error() =
        runBlocking {
            val useCase =
                AddProductToStashUseCase(
                    productRepository = FakeProductRepository(listOf(sampleProduct())),
                    stashRepository = FakeStashRepository(),
                    stashOwnerProvider = localOwnerProvider(),
                    transactionProvider = FakeTransactionProvider(),
                    dateProvider = FixedDateProvider(),
                    logger = NoOpLogger,
                )

            val zeroResult =
                useCase.add(
                    productId = com.maksimowiczm.foodyou.food.domain.entity.FoodId.Product(1),
                    measurement = Measurement.Gram(0.0),
                )
            val negativeResult =
                useCase.add(
                    productId = com.maksimowiczm.foodyou.food.domain.entity.FoodId.Product(1),
                    measurement = Measurement.Serving(-1.0),
                )

            assertEquals(
                AddProductToStashError.InvalidMeasurement,
                assertIs<Error<AddProductToStashResult, AddProductToStashError>>(zeroResult).error,
            )
            assertEquals(
                AddProductToStashError.InvalidMeasurement,
                assertIs<Error<AddProductToStashResult, AddProductToStashError>>(negativeResult)
                    .error,
            )
        }

    @Test
    fun when_multiple_stashes_exist_and_target_is_selected_then_it_adds_product_to_selected_stash() =
        runBlocking {
            val product = sampleProduct()
            val freezer = sampleStash(id = 1L, name = "Freezer", ordering = 0)
            val pantry = sampleStash(id = 2L, name = "Pantry", ordering = 1)
            val stashRepository = FakeStashRepository(initialStashes = listOf(freezer, pantry))
            val useCase =
                AddProductToStashUseCase(
                    productRepository = FakeProductRepository(listOf(product)),
                    stashRepository = stashRepository,
                    stashOwnerProvider = localOwnerProvider(),
                    transactionProvider = FakeTransactionProvider(),
                    dateProvider = FixedDateProvider(),
                    logger = NoOpLogger,
                )

            val result =
                useCase.add(
                    productId = product.id,
                    measurement = Measurement.Gram(250.0),
                    stashId = pantry.id,
                )

            val success = assertIs<Success<AddProductToStashResult, AddProductToStashError>>(result)
            assertEquals(StashDefinitionId(2L), success.data.stashId)
            assertEquals(StashDefinitionId(2L), stashRepository.allItems().single().stashId)
        }

    @Test
    fun when_multiple_stashes_exist_without_selection_then_it_requires_explicit_choice() =
        runBlocking {
            val useCase =
                AddProductToStashUseCase(
                    productRepository = FakeProductRepository(listOf(sampleProduct())),
                    stashRepository =
                        FakeStashRepository(
                            initialStashes =
                                listOf(
                                    sampleStash(id = 1L, name = "Freezer", ordering = 0),
                                    sampleStash(id = 2L, name = "Pantry", ordering = 1),
                                )
                        ),
                    stashOwnerProvider = localOwnerProvider(),
                    transactionProvider = FakeTransactionProvider(),
                    dateProvider = FixedDateProvider(),
                    logger = NoOpLogger,
                )

            val result =
                useCase.add(
                    productId = com.maksimowiczm.foodyou.food.domain.entity.FoodId.Product(1),
                    measurement = Measurement.Gram(100.0),
                )

            assertEquals(
                AddProductToStashError.StashSelectionRequired,
                assertIs<Error<AddProductToStashResult, AddProductToStashError>>(result).error,
            )
        }

    @Test
    fun when_same_product_is_added_twice_with_same_measurement_type_then_item_is_merged() =
        runBlocking {
            val product = sampleProduct(servingWeight = 100.0)
            val stashRepository = FakeStashRepository(initialStashes = listOf(sampleStash()))
            val useCase =
                AddProductToStashUseCase(
                    productRepository = FakeProductRepository(listOf(product)),
                    stashRepository = stashRepository,
                    stashOwnerProvider = localOwnerProvider(),
                    transactionProvider = FakeTransactionProvider(),
                    dateProvider = FixedDateProvider(),
                    logger = NoOpLogger,
                )

            val firstResult =
                useCase.add(productId = product.id, measurement = Measurement.Serving(2.0))
            val secondResult =
                useCase.add(productId = product.id, measurement = Measurement.Serving(3.0))

            val firstSuccess =
                assertIs<Success<AddProductToStashResult, AddProductToStashError>>(firstResult)
            val secondSuccess =
                assertIs<Success<AddProductToStashResult, AddProductToStashError>>(secondResult)

            assertEquals(firstSuccess.data.itemId, secondSuccess.data.itemId)
            assertEquals(1, stashRepository.allItems().size)
            assertEquals(
                StashMeasurement.servings(5.0),
                stashRepository.allItems().single().measurement,
            )
            assertEquals(
                listOf(StashMeasurement.servings(2.0), StashMeasurement.servings(3.0)),
                stashRepository.allMovements().map { it.measurementChange },
            )
        }

    @Test
    fun when_existing_product_item_uses_different_measurement_type_then_new_add_creates_separate_item() =
        runBlocking {
            val product = sampleProduct()
            val existingItem =
                sampleRawProductItem(
                    id = 1L,
                    measurement = StashMeasurement.milliliters(250.0),
                    product = product,
                )
            val stashRepository =
                FakeStashRepository(
                    initialStashes = listOf(sampleStash()),
                    initialItems = listOf(existingItem),
                )
            val useCase =
                AddProductToStashUseCase(
                    productRepository = FakeProductRepository(listOf(product)),
                    stashRepository = stashRepository,
                    stashOwnerProvider = localOwnerProvider(),
                    transactionProvider = FakeTransactionProvider(),
                    dateProvider = FixedDateProvider(),
                    logger = NoOpLogger,
                )

            val result = useCase.add(productId = product.id, measurement = Measurement.Gram(200.0))

            assertIs<Success<AddProductToStashResult, AddProductToStashError>>(result)
            assertEquals(2, stashRepository.allItems().size)
            assertEquals(
                listOf(StashMeasurement.milliliters(250.0), StashMeasurement.grams(200.0)),
                stashRepository.allItems().map { it.measurement },
            )
            assertEquals(
                StashMeasurement.grams(200.0),
                stashRepository.allMovements().single().measurementChange,
            )
        }

    @Test
    fun when_same_product_added_with_grams_and_servings_then_two_separate_items_are_created() =
        runBlocking {
            val product = sampleProduct(servingWeight = 100.0)
            val stashRepository = FakeStashRepository(initialStashes = listOf(sampleStash()))
            val useCase =
                AddProductToStashUseCase(
                    productRepository = FakeProductRepository(listOf(product)),
                    stashRepository = stashRepository,
                    stashOwnerProvider = localOwnerProvider(),
                    transactionProvider = FakeTransactionProvider(),
                    dateProvider = FixedDateProvider(),
                    logger = NoOpLogger,
                )

            val gramResult =
                useCase.add(productId = product.id, measurement = Measurement.Gram(300.0))
            val servingResult =
                useCase.add(productId = product.id, measurement = Measurement.Serving(2.0))

            assertIs<Success<AddProductToStashResult, AddProductToStashError>>(gramResult)
            assertIs<Success<AddProductToStashResult, AddProductToStashError>>(servingResult)

            assertEquals(2, stashRepository.allItems().size)
            val items = stashRepository.allItems().sortedBy { it.measurement.type.ordinal }
            assertEquals(StashMeasurement.grams(300.0), items[0].measurement)
            assertEquals(StashMeasurement.servings(2.0), items[1].measurement)
            assertEquals(2, stashRepository.allMovements().size)
        }
}
