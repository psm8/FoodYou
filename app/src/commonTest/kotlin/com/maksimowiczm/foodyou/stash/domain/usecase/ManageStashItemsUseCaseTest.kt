package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

class ManageStashItemsUseCaseTest {
    private val transactionProvider = FakeTransactionProvider()

    @Test
    fun `when manually setting quantity, it updates the item and records a note`() =
        runBlocking {
            val item = sampleRawProductItem(quantity = StashQuantity.grams(500.0))
            val repository =
                FakeStashRepository(
                    initialStashes = listOf(sampleStash()),
                    initialItems = listOf(item),
                )
            val useCase =
                AdjustStashItemQuantityUseCase(
                    stashRepository = repository,
                    stashOwnerProvider = localOwnerProvider(),
                    dateProvider = FixedDateProvider(),
                    transactionProvider = transactionProvider,
                    logger = NoOpLogger,
                )

            val result =
                useCase.adjust(
                    itemId = item.id,
                    adjustment = StashQuantityAdjustment.SetTo(StashQuantity.grams(300.0)),
                    action = ManualStashAction(reason = "Spoilage", note = "Trimmed moldy edge"),
                )

            val updated = assertIs<Result.Success<*, *>>(result).data as com.maksimowiczm.foodyou.stash.domain.entity.StashItem
            assertEquals(300.0, updated.quantity.amount)
            val movement = repository.allMovements().single()
            assertEquals(-200.0, movement.quantityChange.amount)
            assertEquals("Spoilage\nTrimmed moldy edge", movement.note)
        }

    @Test
    fun `when manually adjusting product-backed raw item to zero, it preserves the row for metadata restore`() =
        runBlocking {
            val item = sampleRawProductItem(quantity = StashQuantity.grams(500.0))
            val repository =
                FakeStashRepository(
                    initialStashes = listOf(sampleStash()),
                    initialItems = listOf(item),
                )
            val useCase =
                AdjustStashItemQuantityUseCase(
                    stashRepository = repository,
                    stashOwnerProvider = localOwnerProvider(),
                    dateProvider = FixedDateProvider(),
                    transactionProvider = transactionProvider,
                    logger = NoOpLogger,
                )

            val result =
                useCase.adjust(
                    itemId = item.id,
                    adjustment = StashQuantityAdjustment.ChangeBy(StashQuantity.grams(-500.0)),
                    action = ManualStashAction(reason = "Correction"),
                )

            val updated = assertIs<Result.Success<*, *>>(result).data as com.maksimowiczm.foodyou.stash.domain.entity.StashItem
            assertEquals(StashQuantity.grams(0.0), updated.quantity)
            assertEquals(1, repository.allItems().size)
            assertEquals(item.snapshot, repository.allItems().single().snapshot)
            assertEquals(StashQuantity.grams(0.0), repository.allItems().single().quantity)
            val movement = repository.allMovements().single()
            assertEquals(StashQuantity.grams(-500.0), movement.quantityChange)
            assertEquals("Correction", movement.note)
        }

    @Test
    fun `when manually adjusting anonymous dish item to zero, it deletes the item`() =
        runBlocking {
            val item = sampleAnonymousDishItem(quantity = StashQuantity.fraction(1.0))
            val repository =
                FakeStashRepository(
                    initialStashes = listOf(sampleStash()),
                    initialItems = listOf(item),
                )
            val useCase =
                AdjustStashItemQuantityUseCase(
                    stashRepository = repository,
                    stashOwnerProvider = localOwnerProvider(),
                    dateProvider = FixedDateProvider(),
                    transactionProvider = transactionProvider,
                    logger = NoOpLogger,
                )

            val result =
                useCase.adjust(
                    itemId = item.id,
                    adjustment = StashQuantityAdjustment.ChangeBy(StashQuantity.fraction(-1.0)),
                    action = ManualStashAction(reason = "Finished"),
                )

            val updated = assertIs<Result.Success<*, *>>(result).data as com.maksimowiczm.foodyou.stash.domain.entity.StashItem
            assertEquals(StashQuantity.fraction(0.0), updated.quantity)
            assertEquals(emptyList(), repository.allItems())
            val movement = repository.allMovements().single()
            assertEquals(StashQuantity.fraction(-1.0), movement.quantityChange)
            assertEquals("Finished", movement.note)
        }

    @Test
    fun `when removing item, it deletes the item and logs the full removal`() =
        runBlocking {
            val item = sampleRawProductItem(quantity = StashQuantity.grams(250.0))
            val repository =
                FakeStashRepository(
                    initialStashes = listOf(sampleStash()),
                    initialItems = listOf(item),
                )
            val useCase =
                RemoveStashItemUseCase(
                    stashRepository = repository,
                    stashOwnerProvider = localOwnerProvider(),
                    dateProvider = FixedDateProvider(),
                    transactionProvider = transactionProvider,
                    logger = NoOpLogger,
                )

            val result =
                useCase.remove(
                    itemId = item.id,
                    action = ManualStashAction(reason = "Discarded", note = "Expired yesterday"),
                )

            val removed = assertIs<Result.Success<*, *>>(result).data as com.maksimowiczm.foodyou.stash.domain.entity.StashItem
            assertEquals(0.0, removed.quantity.amount)
            assertEquals(emptyList(), repository.allItems())
            val movement = repository.allMovements().single()
            assertEquals(-250.0, movement.quantityChange.amount)
            assertEquals("Discarded\nExpired yesterday", movement.note)
        }

    @Test
    fun `when moving item to another stash, it updates the stash id and records both sides of the move`() =
        runBlocking {
            val freezer = sampleStash(id = 1L, name = "Freezer")
            val pantry = sampleStash(id = 2L, name = "Pantry", ordering = 1)
            val item = sampleRawProductItem(stashId = freezer.id.value, quantity = StashQuantity.grams(750.0))
            val repository =
                FakeStashRepository(
                    initialStashes = listOf(freezer, pantry),
                    initialItems = listOf(item),
                )
            val useCase =
                MoveStashItemUseCase(
                    stashRepository = repository,
                    stashOwnerProvider = localOwnerProvider(),
                    dateProvider = FixedDateProvider(),
                    transactionProvider = transactionProvider,
                    logger = NoOpLogger,
                )

            val result = useCase.move(itemId = item.id, targetStashId = pantry.id)

            val moved = assertIs<Result.Success<*, *>>(result).data as com.maksimowiczm.foodyou.stash.domain.entity.StashItem
            assertEquals(pantry.id, moved.stashId)
            assertEquals(
                listOf(
                    Triple(freezer.id, -750.0, "Moved to Pantry"),
                    Triple(pantry.id, 750.0, "Moved from Freezer"),
                ),
                repository.allMovements().map { Triple(it.stashId, it.quantityChange.amount, it.note) },
            )
        }

    @Test
    fun `when adjustment uses mismatched unit, it returns a unit mismatch error`() =
        runBlocking {
            val item = sampleRawProductItem(quantity = StashQuantity.grams(500.0))
            val repository =
                FakeStashRepository(
                    initialStashes = listOf(sampleStash()),
                    initialItems = listOf(item),
                )
            val useCase =
                AdjustStashItemQuantityUseCase(
                    stashRepository = repository,
                    stashOwnerProvider = localOwnerProvider(),
                    dateProvider = FixedDateProvider(),
                    transactionProvider = transactionProvider,
                    logger = NoOpLogger,
                )

            val result =
                useCase.adjust(
                    itemId = item.id,
                    adjustment = StashQuantityAdjustment.SetTo(StashQuantity.fraction(1.0)),
                    action = ManualStashAction(reason = "Correction"),
                )

            assertEquals(
                AdjustStashItemQuantityError.QuantityUnitMismatch(
                    expected = item.quantity.unit,
                    actual = StashQuantity.fraction(1.0).unit,
                ),
                assertIs<Result.Error<*, *>>(result).error,
            )
        }
}
