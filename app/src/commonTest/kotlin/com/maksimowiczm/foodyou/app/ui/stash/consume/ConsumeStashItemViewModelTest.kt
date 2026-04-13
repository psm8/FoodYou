package com.maksimowiczm.foodyou.app.ui.stash.consume

import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
import com.maksimowiczm.foodyou.stash.domain.usecase.ConsumeFromStashUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeFoodDiaryEntryRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeMealRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeStashRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeTransactionProvider
import com.maksimowiczm.foodyou.stash.domain.usecase.FixedDateProvider
import com.maksimowiczm.foodyou.stash.domain.usecase.NoOpLogger
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleMeal
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleRawProductItem
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleStash
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

class ConsumeStashItemViewModelTest {
    @Test
    fun `when amount is above remaining quantity, consume exposes detailed insufficient quantity error`() =
        runBlocking {
            val mealRepository = FakeMealRepository(listOf(sampleMeal()))
            val stashRepository =
                FakeStashRepository(
                    initialStashes = listOf(sampleStash()),
                    initialItems = listOf(sampleRawProductItem(quantity = StashQuantity.grams(50.0))),
                )
            val diaryRepository = FakeFoodDiaryEntryRepository()
            val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val viewModel =
                ConsumeStashItemViewModel(
                    itemId = sampleRawProductItem().id,
                    stashRepository = stashRepository,
                    mealRepository = mealRepository,
                    dateProvider = FixedDateProvider(),
                    consumeFromStashUseCase =
                        ConsumeFromStashUseCase(
                            stashRepository = stashRepository,
                            entryRepository = diaryRepository,
                            mealRepository = mealRepository,
                            transactionProvider = FakeTransactionProvider(),
                            dateProvider = FixedDateProvider(),
                            logger = NoOpLogger,
                        ),
                    coroutineScope = coroutineScope,
                )
            val events = mutableListOf<ConsumeStashItemEvent>()
            val stateJob = launch { viewModel.state.collect { } }
            val eventJob = launch { viewModel.events.collect(events::add) }

            yield()
            viewModel.selectMeal(sampleMeal().id)
            viewModel.updateAmount("100")

            assertTrue(viewModel.state.value.canSave)

            viewModel.consume()
            yield()

            assertEquals(
                ConsumeStashItemError.InsufficientQuantity(
                    available = StashQuantity.grams(50.0),
                    requested = StashQuantity.grams(100.0),
                ),
                viewModel.state.value.error,
            )
            assertEquals(false, viewModel.state.value.isSaving)
            assertEquals(emptyList(), diaryRepository.allEntries())
            assertEquals(StashQuantity.grams(50.0), stashRepository.allItems().single().quantity)
            assertEquals(emptyList(), events)

            stateJob.cancel()
            eventJob.cancel()
        }
}
