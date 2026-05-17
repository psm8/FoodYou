package com.maksimowiczm.foodyou.app.ui.stash.add

import com.maksimowiczm.foodyou.app.ui.home.stash.HomeStashRecipeSnapshotViewModel
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntry
import com.maksimowiczm.foodyou.stash.domain.entity.AnonymousDishSnapshot
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.usecase.CreateAnonymousDishSnapshotUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeFoodDiaryEntryRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeRecipeRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeStashRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.FixedDateProvider
import com.maksimowiczm.foodyou.stash.domain.usecase.NoOpLogger
import com.maksimowiczm.foodyou.stash.domain.usecase.SnapshottingFakeTransactionProvider
import com.maksimowiczm.foodyou.stash.domain.usecase.localOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleRecipe
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleStash
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

class StashAddRecipeSnapshotIntegrationTest {
    @Test
    fun `when recipe is selected from unified add then snapshot stays immutable and diary stays unchanged`() = runBlocking {
        val stashId = StashDefinitionId(7L)
        val selectedDestination =
            StashAddDestination.from(
                foodId = FoodId.Recipe(1L),
                measurement = Measurement.Gram(125.0),
            )
        val recipeDestination = assertIs<StashAddDestination.RecipeSnapshot>(selectedDestination)
        val recipeRepository = FakeRecipeRepository(listOf(sampleRecipe(id = 1L, servings = 4, totalWeight = 400.0)))
        val stashRepository =
            FakeStashRepository(initialStashes = listOf(sampleStash(id = stashId.value, name = "Fridge")))
        val diaryRepository = FakeFoodDiaryEntryRepository()
        val transactionProvider =
            SnapshottingFakeTransactionProvider(stashRepository, diaryRepository)
        val viewModel =
            HomeStashRecipeSnapshotViewModel(
                recipeId = recipeDestination.recipeId,
                preferredStashId = stashId,
                recipeRepository = recipeRepository,
                stashRepository = stashRepository,
                stashOwnerProvider = localOwnerProvider(),
                createAnonymousDishSnapshotUseCase =
                    CreateAnonymousDishSnapshotUseCase(
                        recipeRepository = recipeRepository,
                        stashRepository = stashRepository,
                        stashOwnerProvider = localOwnerProvider(),
                        transactionProvider = transactionProvider,
                        dateProvider = FixedDateProvider(),
                        logger = NoOpLogger,
                    ),
                coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            )

        yield()
        viewModel.updateTotalAmount("2")
        viewModel.updateServingsMade("8")

        assertNotNull(viewModel.state.value.previewNutritionFacts)

        viewModel.save()
        yield()

        val storedSnapshot = assertIs<AnonymousDishSnapshot>(stashRepository.allItems().single().snapshot)
        assertEquals(stashId, stashRepository.allItems().single().stashId)
        assertEquals(Measurement.Serving(2.0), storedSnapshot.totalAmount)
        assertEquals(800.0, storedSnapshot.totalWeight)
        assertEquals(emptyList<FoodDiaryEntry>(), diaryRepository.allEntries())

        recipeRepository.updateRecipe(
            sampleRecipe(id = 1L, name = "Edited pizza", servings = 4, totalWeight = 1000.0)
        )

        val persistedSnapshot = assertIs<AnonymousDishSnapshot>(stashRepository.allItems().single().snapshot)
        assertEquals("Pizza", persistedSnapshot.name)
        assertEquals(Measurement.Serving(2.0), persistedSnapshot.totalAmount)
        assertEquals(800.0, persistedSnapshot.totalWeight)
        assertEquals(storedSnapshot.nutritionFacts, persistedSnapshot.nutritionFacts)
    }

    @Test
    fun `when product is selected from unified add then product destination keeps measurement`() {
        val destination =
            StashAddDestination.from(
                foodId = FoodId.Product(5L),
                measurement = Measurement.Serving(2.0),
            )

        val productDestination = assertIs<StashAddDestination.Product>(destination)
        assertEquals(FoodId.Product(5L), productDestination.productId)
        assertEquals(Measurement.Serving(2.0), productDestination.measurement)
    }
}
