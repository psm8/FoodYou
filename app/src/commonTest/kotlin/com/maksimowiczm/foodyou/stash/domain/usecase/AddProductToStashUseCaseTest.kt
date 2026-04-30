package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.result.Result.Error
import com.maksimowiczm.foodyou.common.result.Result.Success
import com.maksimowiczm.foodyou.stash.domain.entity.RawProductSnapshot
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import com.maksimowiczm.foodyou.stash.domain.entity.StashName
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
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
                Measurement.Gram(250.0),
                stashRepository.allItems().single().rawMeasurement,
            )
            assertEquals(
                StashMovementOperation.Purchase,
                stashRepository.allMovements().single().operation,
            )
            assertEquals(
                StashQuantity.grams(250.0),
                stashRepository.allMovements().single().quantityChange,
            )
            assertEquals(
                Measurement.Gram(250.0),
                stashRepository.allMovements().single().rawMeasurement,
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
    fun when_same_product_is_added_twice_with_equivalent_measurements_then_item_is_merged_and_movements_keep_raw_measurements() =
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
                useCase.add(productId = product.id, measurement = Measurement.Gram(100.0))
            val secondResult =
                useCase.add(productId = product.id, measurement = Measurement.Serving(1.0))

            val firstSuccess =
                assertIs<Success<AddProductToStashResult, AddProductToStashError>>(firstResult)
            val secondSuccess =
                assertIs<Success<AddProductToStashResult, AddProductToStashError>>(secondResult)

            assertEquals(firstSuccess.data.itemId, secondSuccess.data.itemId)
            assertEquals(1, stashRepository.allItems().size)
            assertEquals(StashQuantity.grams(200.0), stashRepository.allItems().single().quantity)
            assertEquals(
                Measurement.Serving(1.0),
                stashRepository.allItems().single().rawMeasurement,
            )
            assertEquals(
                listOf(Measurement.Gram(100.0), Measurement.Serving(1.0)),
                stashRepository.allMovements().map { it.rawMeasurement },
            )
            assertEquals(
                listOf(StashQuantity.grams(100.0), StashQuantity.grams(100.0)),
                stashRepository.allMovements().map { it.quantityChange },
            )
        }

    @Test
    fun when_existing_product_item_uses_different_unit_then_new_add_creates_separate_item() =
        runBlocking {
            val product = sampleProduct()
            val existingItem =
                sampleRawProductItem(
                    id = 1L,
                    quantity = StashQuantity.milliliters(250.0),
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

            val success = assertIs<Success<AddProductToStashResult, AddProductToStashError>>(result)
            assertEquals(2, stashRepository.allItems().size)
            assertEquals(
                listOf(StashQuantity.milliliters(250.0), StashQuantity.grams(200.0)),
                stashRepository.allItems().map { it.quantity },
            )
            assertEquals(
                StashQuantity.grams(200.0),
                stashRepository.allMovements().single().quantityChange,
            )
            assertEquals(
                Measurement.Gram(200.0),
                stashRepository.allMovements().single().rawMeasurement,
            )
            assertEquals(success.data.itemId, stashRepository.allItems().last().id)
        }
}
