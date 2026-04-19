package com.maksimowiczm.foodyou.app.ui.stash.add

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.repository.FoodMeasurementSuggestionRepository
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveFoodUseCase
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveMeasurementSuggestionsUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.AddProductToStashUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeProductRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeRecipeRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeStashRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeTransactionProvider
import com.maksimowiczm.foodyou.stash.domain.usecase.FixedDateProvider
import com.maksimowiczm.foodyou.stash.domain.usecase.NoOpLogger
import com.maksimowiczm.foodyou.stash.domain.usecase.localOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleProduct
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleStash
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

class StashAddProductViewModelTest {
    @Test
    fun `when product is missing, it becomes not found instead of loading forever`() = runBlocking {
        val viewModel = viewModel(productRepository = FakeProductRepository())

        yield()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(true, viewModel.state.value.isProductMissing)
        assertEquals(StashAddProductError.ProductNotFound, viewModel.state.value.error)
    }

    @Test
    fun `when multiple stashes exist and none is selected, saving exposes selection error`() =
        runBlocking {
            val viewModel =
                viewModel(
                    stashRepository =
                        FakeStashRepository(
                            initialStashes =
                                listOf(
                                    sampleStash(id = 1L, name = "Fridge", ordering = 0),
                                    sampleStash(id = 2L, name = "Pantry", ordering = 1),
                                )
                        )
                )

            yield()
            viewModel.save(Measurement.Gram(100.0))

            assertEquals(StashAddProductError.StashSelectionRequired, viewModel.state.value.error)
        }

    @Test
    fun `when initial measurement is provided, state keeps diary-style default selection`() =
        runBlocking {
            val viewModel = viewModel(initialMeasurement = Measurement.Serving(1.5))

            yield()

            assertEquals(Measurement.Serving(1.5), viewModel.state.value.selectedMeasurement)
        }

    private fun viewModel(
        productRepository: FakeProductRepository = FakeProductRepository(listOf(sampleProduct())),
        stashRepository: FakeStashRepository = FakeStashRepository(),
        initialMeasurement: Measurement? = null,
    ): StashAddProductViewModel {
        val observeFoodUseCase =
            ObserveFoodUseCase(
                productRepository = productRepository,
                recipeRepository = FakeRecipeRepository(),
            )

        return StashAddProductViewModel(
            productId = FoodId.Product(1L),
            preferredStashId = null,
            initialMeasurement = initialMeasurement,
            productRepository = productRepository,
            stashRepository = stashRepository,
            stashOwnerProvider = localOwnerProvider(),
            observeMeasurementSuggestionsUseCase =
                ObserveMeasurementSuggestionsUseCase(
                    observeFoodUseCase = observeFoodUseCase,
                    repository = FakeMeasurementSuggestionRepository(),
                ),
            addProductToStashUseCase =
                AddProductToStashUseCase(
                    productRepository = productRepository,
                    stashRepository = stashRepository,
                    stashOwnerProvider = localOwnerProvider(),
                    transactionProvider = FakeTransactionProvider(),
                    dateProvider = FixedDateProvider(),
                    logger = NoOpLogger,
                ),
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
    }
}

private class FakeMeasurementSuggestionRepository(
    private val suggestions: List<Measurement> = emptyList()
) : FoodMeasurementSuggestionRepository {
    override suspend fun insert(foodId: FoodId, measurement: Measurement) = Unit

    override fun observeByFoodId(foodId: FoodId, limit: Int) = flowOf(suggestions.take(limit))
}
