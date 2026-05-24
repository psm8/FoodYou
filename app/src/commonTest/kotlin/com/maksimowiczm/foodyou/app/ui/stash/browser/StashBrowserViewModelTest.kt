package com.maksimowiczm.foodyou.app.ui.stash.browser

import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveFoodUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeProductRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeRecipeRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeStashRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.AdjustStashItemQuantityError
import com.maksimowiczm.foodyou.stash.domain.usecase.MoveStashItemError
import com.maksimowiczm.foodyou.stash.domain.usecase.RemoveStashItemError
import com.maksimowiczm.foodyou.stash.domain.usecase.localOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleAnonymousDishItem
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleProduct
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleRawProductItem
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleRecipe
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleStash
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

class StashBrowserViewModelTest {
    @Test
    fun `when stash contains live product and recipe refs, browser exposes live names`() = runBlocking {
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
            StashBrowserViewModel(
                stashId = sampleStash().id,
                stashRepository = stashRepository,
                stashOwnerProvider = localOwnerProvider(),
                observeFoodUseCase =
                    ObserveFoodUseCase(
                        productRepository = FakeProductRepository(listOf(sampleProduct(name = "Skyr", brand = "FoodYou"))),
                        recipeRepository = FakeRecipeRepository(listOf(sampleRecipe(name = "Pizza"))),
                    ),
                browserActions = object : StashBrowserActions {
                    override suspend fun remove(
                        itemId: com.maksimowiczm.foodyou.stash.domain.entity.StashEntryId,
                        action: com.maksimowiczm.foodyou.stash.domain.usecase.ManualStashAction,
                    ): Result<StashEntry, RemoveStashItemError> = Ok(sampleRawProductItem())

                    override suspend fun adjust(
                        itemId: com.maksimowiczm.foodyou.stash.domain.entity.StashEntryId,
                        adjustment: com.maksimowiczm.foodyou.stash.domain.usecase.StashMeasurementAdjustment,
                        action: com.maksimowiczm.foodyou.stash.domain.usecase.ManualStashAction,
                    ): Result<StashEntry, AdjustStashItemQuantityError> = Ok(sampleRawProductItem())

                    override suspend fun move(
                        itemId: com.maksimowiczm.foodyou.stash.domain.entity.StashEntryId,
                        targetStashId: com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId,
                        action: com.maksimowiczm.foodyou.stash.domain.usecase.ManualStashAction,
                    ): Result<StashEntry, MoveStashItemError> = Ok(sampleRawProductItem())
                },
                coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            )

        val stateJob = launch { viewModel.state.collect { } }
        repeat(20) {
            if (!viewModel.state.value.isLoading && viewModel.state.value.items.isNotEmpty()) {
                return@repeat
            }
            yield()
        }

        assertEquals(listOf("Pizza", "Skyr (FoodYou)"), viewModel.state.value.items.map { it.name })
        assertEquals(false, viewModel.state.value.isLoading)
        stateJob.cancel()
    }
}
