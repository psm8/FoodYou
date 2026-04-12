package com.maksimowiczm.foodyou.app.ui.home.stash

import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.stash.domain.usecase.AddProductToStashUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeProductRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeStashRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeTransactionProvider
import com.maksimowiczm.foodyou.stash.domain.usecase.FixedDateProvider
import com.maksimowiczm.foodyou.stash.domain.usecase.NoOpLogger
import com.maksimowiczm.foodyou.stash.domain.usecase.localOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleProduct
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleStash
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.yield

class HomeStashQuickAddViewModelTest {
    @Test
    fun `when product is missing, it becomes not found instead of loading forever`() = runBlocking {
        val viewModel = quickAddViewModel(productRepository = FakeProductRepository())

        yield()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(true, viewModel.state.value.isProductMissing)
        assertEquals(HomeStashQuickAddError.ProductNotFound, viewModel.state.value.error)
    }

    @Test
    fun `when saving without valid amount, it exposes invalid amount error`() = runBlocking {
        val viewModel = quickAddViewModel()

        yield()
        viewModel.save()

        assertEquals(HomeStashQuickAddError.InvalidAmount, viewModel.state.value.error)
    }

    @Test
    fun `when multiple stashes exist and none is selected, saving exposes selection error`() = runBlocking {
        val viewModel =
            quickAddViewModel(
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
        viewModel.updateAmount("100")
        viewModel.save()

        assertEquals(HomeStashQuickAddError.StashSelectionRequired, viewModel.state.value.error)
    }

    private fun quickAddViewModel(
        productRepository: FakeProductRepository = FakeProductRepository(listOf(sampleProduct())),
        stashRepository: FakeStashRepository = FakeStashRepository(),
    ): HomeStashQuickAddViewModel =
        HomeStashQuickAddViewModel(
            productId = FoodId.Product(1L),
            preferredStashId = null,
            productRepository = productRepository,
            stashRepository = stashRepository,
            stashOwnerProvider = localOwnerProvider(),
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
