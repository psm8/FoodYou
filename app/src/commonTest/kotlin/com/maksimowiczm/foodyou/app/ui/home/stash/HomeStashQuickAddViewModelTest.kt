package com.maksimowiczm.foodyou.app.ui.home.stash

import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.stash.domain.usecase.CreateManualStashSnapshotUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeStashRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeTransactionProvider
import com.maksimowiczm.foodyou.stash.domain.usecase.FixedDateProvider
import com.maksimowiczm.foodyou.stash.domain.usecase.NoOpLogger
import com.maksimowiczm.foodyou.stash.domain.usecase.localOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleStash
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HomeStashQuickAddViewModelTest {
    @Test
    fun `when screen starts, it resolves loading state immediately`() = runBlocking {
        val viewModel = quickAddViewModel()

        yield()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(emptyList(), viewModel.state.value.stashes)
    }

    @Test
    fun `when multiple stashes exist and none is selected, saving exposes selection error`() =
        runBlocking {
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
            viewModel.save(
                name = "Quick soup",
                nutritionFacts = NutritionFacts.Empty,
                measurement = Measurement.Milliliter(400.0),
            )

            assertEquals(HomeStashQuickAddError.StashSelectionRequired, viewModel.state.value.error)
        }

    @Test
    fun `when quick add saves successfully, it emits saved event with target stash`() = runBlocking {
        val stashRepository = FakeStashRepository(initialStashes = listOf(sampleStash(id = 7L)))
        val viewModel = quickAddViewModel(stashRepository = stashRepository)

        yield()
        viewModel.save(
            name = "Quick berries",
            nutritionFacts = NutritionFacts.Empty,
            measurement = Measurement.Gram(150.0),
        )

        assertEquals(HomeStashQuickAddEvent.Saved(sampleStash(id = 7L).id), viewModel.events.first())
    }

    private fun quickAddViewModel(
        stashRepository: FakeStashRepository = FakeStashRepository(),
    ): HomeStashQuickAddViewModel =
        HomeStashQuickAddViewModel(
            preferredStashId = null,
            stashRepository = stashRepository,
            stashOwnerProvider = localOwnerProvider(),
            createManualStashSnapshotUseCase =
                CreateManualStashSnapshotUseCase(
                    stashRepository = stashRepository,
                    stashOwnerProvider = localOwnerProvider(),
                    transactionProvider = FakeTransactionProvider(),
                    dateProvider = FixedDateProvider(),
                    logger = NoOpLogger,
                ),
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
}
