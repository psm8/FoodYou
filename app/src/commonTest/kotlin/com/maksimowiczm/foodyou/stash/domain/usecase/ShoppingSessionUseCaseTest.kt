package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.result.Result.Success
import com.maksimowiczm.foodyou.stash.domain.entity.ShoppingSession
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

class ShoppingSessionUseCaseTest {
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

        val started = assertIs<Success<ShoppingSession, StartShoppingSessionError>>(startUseCase.start(sampleStash().id)).data
        val updated =
            assertIs<Success<ShoppingSession, AddToShoppingSessionError>>(
                addUseCase.add(
                    session = started,
                    productId = com.maksimowiczm.foodyou.food.domain.entity.FoodId.Product(1),
                    quantity = StashQuantity.grams(250.0),
                )
            ).data

        assertEquals(1, updated.items.size)
        assertEquals(250.0, updated.totalNutritionFacts.energy.value)
    }

    @Test
    fun when_confirming_session_then_all_items_are_added_to_stash_and_movements_are_recorded() = runBlocking {
        val stashRepository = FakeStashRepository(initialStashes = listOf(sampleStash()))
        val confirmUseCase =
            ConfirmShoppingSessionUseCase(
                stashRepository = stashRepository,
                stashOwnerProvider = localOwnerProvider(),
                transactionProvider = FakeTransactionProvider(),
                dateProvider = FixedDateProvider(),
                logger = NoOpLogger,
            )
        val session =
            ShoppingSession(
                id = com.maksimowiczm.foodyou.stash.domain.entity.ShoppingSessionId("session-1"),
                stashId = sampleStash().id,
                items =
                    listOf(
                        com.maksimowiczm.foodyou.stash.domain.entity.ShoppingSessionItem(
                            productId = com.maksimowiczm.foodyou.food.domain.entity.FoodId.Product(1),
                            snapshot = com.maksimowiczm.foodyou.stash.domain.entity.RawProductSnapshot.from(sampleProduct()),
                            quantity = StashQuantity.grams(200.0),
                        ),
                        com.maksimowiczm.foodyou.stash.domain.entity.ShoppingSessionItem(
                            productId = com.maksimowiczm.foodyou.food.domain.entity.FoodId.Product(2),
                            snapshot =
                                com.maksimowiczm.foodyou.stash.domain.entity.RawProductSnapshot.from(
                                    sampleProduct(id = 2, name = "Yoghurt")
                                ),
                            quantity = StashQuantity.grams(150.0),
                        ),
                    ),
            )

        val result = confirmUseCase.confirm(session)

        val success = assertIs<Success<List<com.maksimowiczm.foodyou.stash.domain.entity.StashItemId>, ConfirmShoppingSessionError>>(result)
        assertEquals(2, success.data.size)
        assertEquals(2, stashRepository.allItems().size)
        assertEquals(2, stashRepository.allMovements().size)
        assertEquals(
            listOf(StashMovementOperation.Purchase, StashMovementOperation.Purchase),
            stashRepository.allMovements().map { it.operation },
        )
    }
}
