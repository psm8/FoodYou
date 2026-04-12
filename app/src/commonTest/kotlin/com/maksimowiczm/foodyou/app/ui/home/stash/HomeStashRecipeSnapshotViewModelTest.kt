package com.maksimowiczm.foodyou.app.ui.home.stash

import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.stash.domain.entity.AnonymousDishSnapshot
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantityUnit
import com.maksimowiczm.foodyou.stash.domain.usecase.CreateAnonymousDishSnapshotUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeRecipeRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeStashRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeTransactionProvider
import com.maksimowiczm.foodyou.stash.domain.usecase.FixedDateProvider
import com.maksimowiczm.foodyou.stash.domain.usecase.NoOpLogger
import com.maksimowiczm.foodyou.stash.domain.usecase.localOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleRecipe
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleStash
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

class HomeStashRecipeSnapshotViewModelTest {
    @Test
    fun `when recipe is missing, it becomes not found instead of loading forever`() = runBlocking {
        val viewModel = snapshotViewModel(recipeRepository = FakeRecipeRepository())

        yield()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(true, viewModel.state.value.isRecipeMissing)
        assertEquals(HomeStashRecipeSnapshotError.RecipeNotFound, viewModel.state.value.error)
    }

    @Test
    fun `when saving without valid amount, it exposes invalid amount error`() = runBlocking {
        val viewModel = snapshotViewModel()

        yield()
        viewModel.save()

        assertEquals(HomeStashRecipeSnapshotError.InvalidAmount, viewModel.state.value.error)
    }

    @Test
    fun `when saving without valid servings, it exposes invalid servings error`() = runBlocking {
        val viewModel = snapshotViewModel()

        yield()
        viewModel.updateTotalAmount("2")
        viewModel.updateServingsMade("0")
        viewModel.save()

        assertEquals(HomeStashRecipeSnapshotError.InvalidServings, viewModel.state.value.error)
    }

    @Test
    fun `when multiple stashes exist and none is selected, saving exposes selection error`() = runBlocking {
        val viewModel =
            snapshotViewModel(
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
        viewModel.updateTotalAmount("2")
        viewModel.updateServingsMade("6")
        viewModel.save()

        assertEquals(HomeStashRecipeSnapshotError.StashSelectionRequired, viewModel.state.value.error)
    }

    @Test
    fun `when form is valid, it creates anonymous dish snapshot in stash`() = runBlocking {
        val stashRepository = FakeStashRepository(initialStashes = listOf(sampleStash()))
        val viewModel = snapshotViewModel(stashRepository = stashRepository)

        yield()
        viewModel.updateTotalAmount("2")
        assertNotNull(viewModel.state.value.previewNutritionFacts)

        viewModel.save()
        yield()

        val createdSnapshot = assertIs<AnonymousDishSnapshot>(stashRepository.allItems().single().snapshot)
        assertEquals(StashQuantity.fraction(2.0), createdSnapshot.totalAmount)
        assertEquals(sampleRecipe().totalWeight, createdSnapshot.totalWeight)
        assertEquals(null, viewModel.state.value.error)
    }

    @Test
    fun `when gram unit is selected, snapshot keeps gram amount and weight`() = runBlocking {
        val stashRepository = FakeStashRepository(initialStashes = listOf(sampleStash()))
        val viewModel = snapshotViewModel(stashRepository = stashRepository)

        yield()
        viewModel.selectAmountUnit(StashQuantityUnit.Gram)
        viewModel.updateTotalAmount("250")
        viewModel.save()
        yield()

        val createdSnapshot = assertIs<AnonymousDishSnapshot>(stashRepository.allItems().single().snapshot)
        assertEquals(StashQuantity.grams(250.0), createdSnapshot.totalAmount)
        assertEquals(250.0, createdSnapshot.totalWeight)
        assertEquals(StashQuantityUnit.Gram, viewModel.state.value.amountUnit)
    }

    @Test
    fun `when liquid recipe uses milliliters, snapshot keeps milliliter amount and weight`() = runBlocking {
        val stashRepository = FakeStashRepository(initialStashes = listOf(sampleStash()))
        val viewModel =
            snapshotViewModel(
                recipeRepository = FakeRecipeRepository(listOf(sampleRecipe(isLiquid = true))),
                stashRepository = stashRepository,
            )

        yield()
        viewModel.selectAmountUnit(StashQuantityUnit.Milliliter)
        viewModel.updateTotalAmount("500")
        viewModel.save()
        yield()

        val createdSnapshot = assertIs<AnonymousDishSnapshot>(stashRepository.allItems().single().snapshot)
        assertEquals(StashQuantity.milliliters(500.0), createdSnapshot.totalAmount)
        assertEquals(500.0, createdSnapshot.totalWeight)
        assertEquals(StashQuantityUnit.Milliliter, viewModel.state.value.amountUnit)
    }

    private fun snapshotViewModel(
        recipeRepository: FakeRecipeRepository = FakeRecipeRepository(listOf(sampleRecipe())),
        stashRepository: FakeStashRepository = FakeStashRepository(),
    ): HomeStashRecipeSnapshotViewModel =
        HomeStashRecipeSnapshotViewModel(
            recipeId = FoodId.Recipe(1L),
            preferredStashId = null,
            recipeRepository = recipeRepository,
            stashRepository = stashRepository,
            stashOwnerProvider = localOwnerProvider(),
            createAnonymousDishSnapshotUseCase =
                CreateAnonymousDishSnapshotUseCase(
                    recipeRepository = recipeRepository,
                    stashRepository = stashRepository,
                    stashOwnerProvider = localOwnerProvider(),
                    transactionProvider = FakeTransactionProvider(),
                    dateProvider = FixedDateProvider(),
                    logger = NoOpLogger,
                ),
        )
}
