package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.result.Result.Error
import com.maksimowiczm.foodyou.common.result.Result.Success
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.RawProductSnapshot
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import com.maksimowiczm.foodyou.stash.domain.entity.StashName
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

class AddProductToStashUseCaseTest {
    @Test
    fun when_first_product_is_added_then_default_stash_and_purchase_movement_are_created() = runBlocking {
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

        val result = useCase.add(productId = product.id, quantity = StashQuantity.grams(250.0))

        val success = assertIs<Success<AddProductToStashResult, AddProductToStashError>>(result)
        assertEquals(StashName.from("Stash"), stashRepository.allStashes().single().name)
        assertEquals(success.data.stashId, stashRepository.allStashes().single().id)
        assertEquals(
            RawProductSnapshot.from(product),
            stashRepository.allItems().single().snapshot,
        )
        assertEquals(StashMovementOperation.Purchase, stashRepository.allMovements().single().operation)
        assertEquals(StashQuantity.grams(250.0), stashRepository.allMovements().single().quantityChange)
    }

    @Test
    fun when_quantity_unit_is_invalid_for_product_then_it_returns_validation_error() = runBlocking {
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
                quantity = StashQuantity.grams(100.0),
            )

        assertEquals(AddProductToStashError.InvalidQuantityUnit, assertIs<Error<AddProductToStashResult, AddProductToStashError>>(result).error)
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
                    quantity = StashQuantity.grams(250.0),
                    stashId = pantry.id,
                )

            val success = assertIs<Success<AddProductToStashResult, AddProductToStashError>>(result)
            assertEquals(StashDefinitionId(2L), success.data.stashId)
            assertEquals(StashDefinitionId(2L), stashRepository.allItems().single().stashId)
        }

    @Test
    fun when_multiple_stashes_exist_without_selection_then_it_requires_explicit_choice() = runBlocking {
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
                quantity = StashQuantity.grams(100.0),
            )

        assertEquals(
            AddProductToStashError.StashSelectionRequired,
            assertIs<Error<AddProductToStashResult, AddProductToStashError>>(result).error,
        )
    }
}
