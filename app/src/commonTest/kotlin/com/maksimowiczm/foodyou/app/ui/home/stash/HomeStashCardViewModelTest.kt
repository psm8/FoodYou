package com.maksimowiczm.foodyou.app.ui.home.stash

import com.maksimowiczm.foodyou.food.domain.usecase.ObserveFoodUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeProductRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeRecipeRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeStashRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.ObserveHomeStashSummaryUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.localOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleAnonymousDishItem
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleProduct
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleRawProductItem
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleRecipe
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleStash
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

class HomeStashCardViewModelTest {
    @Test
    fun `when home stash summary has live refs, card exposes live item names`() = runBlocking {
        val stashRepository =
            FakeStashRepository(
                initialStashes = listOf(sampleStash()),
                initialItems =
                    listOf(
                        sampleRawProductItem(id = 1L, product = sampleProduct(name = "Skyr", brand = "FoodYou")),
                        sampleAnonymousDishItem(id = 2L, recipe = sampleRecipe(name = "Pizza")),
                    ),
            )

        val viewModel =
            HomeStashCardViewModel(
                observeHomeStashSummaryUseCase =
                    ObserveHomeStashSummaryUseCase(
                        stashRepository = stashRepository,
                        stashOwnerProvider = localOwnerProvider(),
                    ),
                observeFoodUseCase =
                    ObserveFoodUseCase(
                        productRepository = FakeProductRepository(listOf(sampleProduct(name = "Skyr", brand = "FoodYou"))),
                        recipeRepository = FakeRecipeRepository(listOf(sampleRecipe(name = "Pizza"))),
                    ),
                coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            )

        val stateJob = launch { viewModel.state.collect { } }
        repeat(20) {
            if (!viewModel.state.value.isLoading && viewModel.state.value.recentItems.isNotEmpty()) {
                return@repeat
            }
            yield()
        }

        assertEquals(listOf("Pizza", "Skyr (FoodYou)"), viewModel.state.value.recentItems.map { it.name })
        assertEquals(false, viewModel.state.value.isLoading)
        stateJob.cancel()
    }
}
