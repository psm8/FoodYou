package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.result.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

class ManageStashesUseCaseTest {
    private val transactionProvider = FakeTransactionProvider()

    @Test
    fun `when creating first stash, it uses the next ordering slot`() =
        runBlocking {
            val repository = FakeStashRepository()
            val useCase =
                CreateStashUseCase(
                    stashRepository = repository,
                    stashOwnerProvider = localOwnerProvider(),
                    dateProvider = FixedDateProvider(),
                    transactionProvider = transactionProvider,
                    logger = NoOpLogger,
                )

            val result = useCase.create("Freezer")

            val created = assertIs<Result.Success<*, *>>(result).data as com.maksimowiczm.foodyou.stash.domain.entity.StashDefinition
            assertEquals("Freezer", created.name.value)
            assertEquals(0, created.ordering)
            assertEquals(listOf("Freezer"), repository.allStashes().map { it.name.value })
        }

    @Test
    fun `when creating duplicate stash, it returns duplicate name error`() =
        runBlocking {
            val repository = FakeStashRepository(initialStashes = listOf(sampleStash(name = "Freezer")))
            val useCase =
                CreateStashUseCase(
                    stashRepository = repository,
                    stashOwnerProvider = localOwnerProvider(),
                    dateProvider = FixedDateProvider(),
                    transactionProvider = transactionProvider,
                    logger = NoOpLogger,
                )

            val result = useCase.create("Freezer")

            assertEquals(CreateStashError.DuplicateName("Freezer"), assertIs<Result.Error<*, *>>(result).error)
        }

    @Test
    fun `when renaming stash, it updates the stored name`() =
        runBlocking {
            val repository = FakeStashRepository(initialStashes = listOf(sampleStash(name = "Fridge")))
            val useCase =
                RenameStashUseCase(
                    stashRepository = repository,
                    stashOwnerProvider = localOwnerProvider(),
                    transactionProvider = transactionProvider,
                    logger = NoOpLogger,
                )

            val result = useCase.rename(sampleStash().id, "Pantry")

            val renamed = assertIs<Result.Success<*, *>>(result).data as com.maksimowiczm.foodyou.stash.domain.entity.StashDefinition
            assertEquals("Pantry", renamed.name.value)
            assertEquals(listOf("Pantry"), repository.allStashes().map { it.name.value })
        }

    @Test
    fun `when reordering stashes, it rewrites ordering indexes to match requested order`() =
        runBlocking {
            val freezer = sampleStash(id = 1L, name = "Freezer", ordering = 0)
            val fridge = sampleStash(id = 2L, name = "Fridge", ordering = 1)
            val pantry = sampleStash(id = 3L, name = "Pantry", ordering = 2)
            val repository = FakeStashRepository(initialStashes = listOf(freezer, fridge, pantry))
            val useCase =
                ReorderStashesUseCase(
                    stashRepository = repository,
                    stashOwnerProvider = localOwnerProvider(),
                    transactionProvider = transactionProvider,
                    logger = NoOpLogger,
                )

            val result = useCase.reorder(listOf(pantry.id, freezer.id, fridge.id))

            val reordered = assertIs<Result.Success<*, *>>(result).data as List<*>
            assertEquals(listOf("Pantry", "Freezer", "Fridge"), reordered.map { (it as com.maksimowiczm.foodyou.stash.domain.entity.StashDefinition).name.value })
            assertEquals(listOf("Pantry", "Freezer", "Fridge"), repository.allStashes().sortedBy { it.ordering }.map { it.name.value })
        }

    @Test
    fun `when deleting stash, it compacts remaining ordering`() =
        runBlocking {
            val freezer = sampleStash(id = 1L, name = "Freezer", ordering = 0)
            val fridge = sampleStash(id = 2L, name = "Fridge", ordering = 1)
            val pantry = sampleStash(id = 3L, name = "Pantry", ordering = 2)
            val repository = FakeStashRepository(initialStashes = listOf(freezer, fridge, pantry))
            val useCase =
                DeleteStashUseCase(
                    stashRepository = repository,
                    stashOwnerProvider = localOwnerProvider(),
                    transactionProvider = transactionProvider,
                    logger = NoOpLogger,
                )

            val result = useCase.delete(fridge.id)

            assertIs<Result.Success<*, *>>(result)
            assertEquals(
                listOf("Freezer" to 0, "Pantry" to 1),
                repository.allStashes().sortedBy { it.ordering }.map { it.name.value to it.ordering },
            )
        }
}
