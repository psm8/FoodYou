package com.maksimowiczm.foodyou.app.ui.stash.shopping

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.search.domain.FoodSearch
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
import com.maksimowiczm.foodyou.stash.domain.usecase.AddToShoppingSessionUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.ConfirmShoppingSessionUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeProductRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeStashRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.FixedDateProvider
import com.maksimowiczm.foodyou.stash.domain.usecase.NoOpLogger
import com.maksimowiczm.foodyou.stash.domain.usecase.SnapshottingFakeTransactionProvider
import com.maksimowiczm.foodyou.stash.domain.usecase.StartShoppingSessionUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.localOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.usecase.nutritionWithEnergy
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleProduct
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleStash
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

class ShoppingSessionViewModelTest {
    @Test
    fun `when products are added one after another, the preview keeps session state and total calories`() =
        runBlocking {
            val productOne =
                sampleProduct(id = 1L, name = "Skyr", nutritionFacts = nutritionWithEnergy(60.0))
            val productTwo =
                sampleProduct(id = 2L, name = "Rice", nutritionFacts = nutritionWithEnergy(130.0))
            val viewModel = shoppingSessionViewModel(products = listOf(productOne, productTwo))

            awaitState(viewModel) { !it.isLoading && it.selectedStashId != null }
            viewModel.selectProduct(searchProduct(productOne))
            viewModel.updatePendingMeasurement(Measurement.Gram(200.0))
            viewModel.addPendingProduct()
            awaitState(viewModel) { it.items.size == 1 }

            assertEquals(1, viewModel.state.value.items.size)
            assertEquals(120.0, viewModel.state.value.totalCalories)

            viewModel.selectProduct(searchProduct(productTwo))
            assertEquals(1, viewModel.state.value.items.size)
            viewModel.updatePendingMeasurement(Measurement.Gram(500.0))
            viewModel.addPendingProduct()
            awaitState(viewModel) { it.items.size == 2 }

            assertEquals(2, viewModel.state.value.items.size)
            assertEquals(770.0, viewModel.state.value.totalCalories)
        }

    @Test
    fun `when editing an item quantity before confirm, the running calories update`() =
        runBlocking {
            val product =
                sampleProduct(id = 1L, name = "Skyr", nutritionFacts = nutritionWithEnergy(60.0))
            val viewModel = shoppingSessionViewModel(products = listOf(product))

            awaitState(viewModel) { !it.isLoading && it.selectedStashId != null }
            viewModel.selectProduct(searchProduct(product))
            viewModel.updatePendingMeasurement(Measurement.Gram(200.0))
            viewModel.addPendingProduct()
            awaitState(viewModel) { it.items.size == 1 }

            val itemId = viewModel.state.value.items.single().id
            viewModel.updateItemQuantity(itemId, "300")

            assertEquals("300", viewModel.state.value.items.single().quantityText)
            assertEquals(180.0, viewModel.state.value.totalCalories)
        }

    @Test
    fun `when confirming the shopping session, it commits merged items with measurements, emits finish, and clears local state`() =
        runBlocking {
            val stashRepository =
                FakeStashRepository(initialStashes = listOf(sampleStash(id = 9L, name = "Pantry")))
            val productOne =
                sampleProduct(id = 1L, name = "Skyr", nutritionFacts = nutritionWithEnergy(60.0))
            val productTwo =
                sampleProduct(
                    id = 2L,
                    name = "Juice",
                    isLiquid = true,
                    nutritionFacts = nutritionWithEnergy(45.0),
                )
            val viewModel =
                shoppingSessionViewModel(
                    stashId = sampleStash(id = 9L, name = "Pantry").id,
                    stashRepository = stashRepository,
                    products = listOf(productOne, productTwo),
                )

            awaitState(viewModel) { !it.isLoading && it.selectedStashId != null }
            viewModel.selectProduct(searchProduct(productOne))
            viewModel.updatePendingMeasurement(Measurement.Package(1.0))
            viewModel.addPendingProduct()
            awaitState(viewModel) { it.items.size == 1 }
            viewModel.selectProduct(searchProduct(productOne))
            viewModel.updatePendingMeasurement(Measurement.Package(0.5))
            viewModel.addPendingProduct()
            awaitState(viewModel) { it.items.size == 1 && it.items.single().quantityText == "1500" }
            viewModel.selectProduct(searchProduct(productTwo))
            viewModel.updatePendingMeasurement(Measurement.Package(0.4))
            viewModel.addPendingProduct()
            awaitState(viewModel) { it.items.size == 2 }

            val event = async { viewModel.events.first() }
            viewModel.confirm()
            awaitState(viewModel) { !it.isConfirming && it.items.isEmpty() }

            val finished = assertIs<ShoppingSessionEvent.Finished>(event.await())
            assertEquals(StashDefinitionId(9L), finished.stashId)
            assertEquals(2, stashRepository.allItems().size)
            assertEquals(2, stashRepository.allMovements().size)
            assertEquals(StashQuantity.grams(1500.0), stashRepository.allItems().first().quantity)
            assertEquals(
                StashQuantity.milliliters(400.0),
                stashRepository.allItems().last().quantity,
            )
            assertEquals(emptyList(), viewModel.state.value.items)
            assertEquals(0.0, viewModel.state.value.totalCalories)
        }

    @Test
    fun `when confirming with a valid edited quantity, the latest edit is committed`() =
        runBlocking {
            val stashRepository =
                FakeStashRepository(initialStashes = listOf(sampleStash(id = 9L, name = "Pantry")))
            val product =
                sampleProduct(id = 1L, name = "Skyr", nutritionFacts = nutritionWithEnergy(60.0))
            val viewModel =
                shoppingSessionViewModel(
                    stashId = StashDefinitionId(9L),
                    stashRepository = stashRepository,
                    products = listOf(product),
                )

            awaitState(viewModel) { !it.isLoading && it.selectedStashId != null }
            viewModel.selectProduct(searchProduct(product))
            viewModel.updatePendingMeasurement(Measurement.Gram(200.0))
            viewModel.addPendingProduct()
            awaitState(viewModel) { it.items.size == 1 }

            val itemId = viewModel.state.value.items.single().id
            viewModel.updateItemQuantity(itemId, "300")

            val event = async { viewModel.events.first() }
            viewModel.confirm()
            awaitState(viewModel) { !it.isConfirming && it.items.isEmpty() }

            assertIs<ShoppingSessionEvent.Finished>(event.await())
            assertEquals(StashQuantity.grams(300.0), stashRepository.allItems().single().quantity)
        }

    @Test
    fun `when confirming with an edited package quantity, it preserves the package measurement`() =
        runBlocking {
            val stashRepository =
                FakeStashRepository(initialStashes = listOf(sampleStash(id = 9L, name = "Pantry")))
            val product = sampleProduct(id = 1L, name = "Skyr", packageWeight = 1000.0)
            val viewModel =
                shoppingSessionViewModel(
                    stashId = StashDefinitionId(9L),
                    stashRepository = stashRepository,
                    products = listOf(product),
                )

            awaitState(viewModel) { !it.isLoading && it.selectedStashId != null }
            viewModel.selectProduct(searchProduct(product))
            viewModel.updatePendingMeasurement(Measurement.Package(1.5))
            viewModel.addPendingProduct()
            awaitState(viewModel) { it.items.size == 1 }

            val itemId = viewModel.state.value.items.single().id
            viewModel.updateItemQuantity(itemId, "750")

            val event = async { viewModel.events.first() }
            viewModel.confirm()
            awaitState(viewModel) { !it.isConfirming && it.items.isEmpty() }

            assertIs<ShoppingSessionEvent.Finished>(event.await())
            assertEquals(StashQuantity.grams(750.0), stashRepository.allItems().single().quantity)
        }

    @Test
    fun `when a product is selected, the composer starts from the suggested measurement`() =
        runBlocking {
            val product =
                sampleProduct(id = 1L, name = "Skyr", nutritionFacts = nutritionWithEnergy(60.0))
            val viewModel = shoppingSessionViewModel(products = listOf(product))

            awaitState(viewModel) { !it.isLoading && it.selectedStashId != null }
            viewModel.selectProduct(searchProduct(product))

            assertEquals(Measurement.Gram(100.0), viewModel.state.value.pendingMeasurement)
        }

    private fun shoppingSessionViewModel(
        stashId: StashDefinitionId = sampleStash().id,
        stashRepository: FakeStashRepository =
            FakeStashRepository(initialStashes = listOf(sampleStash())),
        products: List<Product> = listOf(sampleProduct()),
    ): ShoppingSessionViewModel {
        val productRepository = FakeProductRepository(products)
        return ShoppingSessionViewModel(
            stashId = stashId,
            startShoppingSessionUseCase =
                StartShoppingSessionUseCase(
                    stashRepository = stashRepository,
                    stashOwnerProvider = localOwnerProvider(),
                    dateProvider = FixedDateProvider(),
                    logger = NoOpLogger,
                ),
            addToShoppingSessionUseCase =
                AddToShoppingSessionUseCase(
                    productRepository = productRepository,
                    logger = NoOpLogger,
                ),
            confirmShoppingSessionUseCase =
                ConfirmShoppingSessionUseCase(
                    stashRepository = stashRepository,
                    stashOwnerProvider = localOwnerProvider(),
                    transactionProvider = SnapshottingFakeTransactionProvider(stashRepository),
                    dateProvider = FixedDateProvider(),
                    logger = NoOpLogger,
                ),
            stashRepository = stashRepository,
            stashOwnerProvider = localOwnerProvider(),
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
    }

    private fun searchProduct(product: Product): FoodSearch.Product =
        FoodSearch.Product(
            id = product.id,
            headline = product.name,
            isLiquid = product.isLiquid,
            nutritionFacts = product.nutritionFacts,
            totalWeight = product.packageWeight,
            servingWeight = product.servingWeight,
            suggestedMeasurement =
                if (product.isLiquid) {
                    Measurement.Milliliter(100.0)
                } else {
                    Measurement.Gram(100.0)
                },
        )

    private suspend fun awaitState(
        viewModel: ShoppingSessionViewModel,
        predicate: (ShoppingSessionUiState) -> Boolean,
    ) {
        repeat(20) {
            if (predicate(viewModel.state.value)) {
                return
            }
            yield()
        }
        error("Shopping session view model did not reach the expected state.")
    }
}
