package com.maksimowiczm.foodyou.app.ui.stash.browser

import com.maksimowiczm.foodyou.stash.domain.usecase.AdjustStashItemQuantityUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeStashRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.FakeTransactionProvider
import com.maksimowiczm.foodyou.stash.domain.usecase.FixedDateProvider
import com.maksimowiczm.foodyou.stash.domain.usecase.ManualStashAction
import com.maksimowiczm.foodyou.stash.domain.usecase.NoOpLogger
import com.maksimowiczm.foodyou.stash.domain.usecase.RemoveStashItemUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.localOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleRawProductItem
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleStash
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class StashBrowserActionsTest {
    @Test
    fun `when moving an item, it records the manual action note for movement history`() =
        runBlocking {
            val sourceStash = sampleStash(id = 1L, name = "Freezer")
            val targetStash = sampleStash(id = 2L, name = "Pantry", ordering = 1)
            val item = sampleRawProductItem(stashId = sourceStash.id.value)
            val repository =
                FakeStashRepository(
                    initialStashes = listOf(sourceStash, targetStash),
                    initialItems = listOf(item),
                )
            val actions = browserActions(repository)

            actions.move(
                itemId = item.id,
                targetStashId = targetStash.id,
                action = ManualStashAction(reason = "Reorganized shelf", note = "Top rack"),
            )

            assertEquals(targetStash.id, repository.getItem(item.id)?.stashId)
            assertEquals(
                listOf("Reorganized shelf\nTop rack", "Reorganized shelf\nTop rack"),
                repository.allMovements().map { it.note },
            )
        }

    private fun browserActions(repository: FakeStashRepository): DomainStashBrowserActions {
        val ownerProvider = localOwnerProvider()
        val dateProvider = FixedDateProvider()
        val transactionProvider = FakeTransactionProvider()
        val logger = NoOpLogger
        return DomainStashBrowserActions(
            adjustStashItemQuantityUseCase =
                AdjustStashItemQuantityUseCase(
                    stashRepository = repository,
                    stashOwnerProvider = ownerProvider,
                    dateProvider = dateProvider,
                    transactionProvider = transactionProvider,
                    logger = logger,
                ),
            removeStashItemUseCase =
                RemoveStashItemUseCase(
                    stashRepository = repository,
                    stashOwnerProvider = ownerProvider,
                    dateProvider = dateProvider,
                    transactionProvider = transactionProvider,
                    logger = logger,
                ),
            stashRepository = repository,
            stashOwnerProvider = ownerProvider,
            dateProvider = dateProvider,
            transactionProvider = transactionProvider,
            logger = logger,
        )
    }
}
