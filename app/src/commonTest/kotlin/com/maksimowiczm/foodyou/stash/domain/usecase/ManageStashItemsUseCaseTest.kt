package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

class ManageStashItemsUseCaseTest {
    private val transactionProvider = FakeTransactionProvider()

    @Test
    fun `when manually setting quantity, it updates the item and records a note`() =
        runBlocking {
            val item = sampleRawProductItem(measurement = StashMeasurement.grams(500.0))
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
                    adjustment = StashMeasurementAdjustment.SetTo(StashMeasurement.grams(300.0)),
                    action = ManualStashAction(reason = "Spoilage", note = "Trimmed moldy edge"),
                )

            val updated = assertIs<Result.Success<*, *>>(result).data as com.maksimowiczm.foodyou.stash.domain.entity.StashEntry
            assertEquals(300.0, updated.measurement.measurement.rawValue)
            val movement = repository.allMovements().single()
            assertEquals(-200.0, movement.measurementChange.measurement.rawValue)
            assertEquals("Spoilage\nTrimmed moldy edge", movement.note)
        }

    @Test
    fun `when manually adjusting product-backed raw item to zero, it preserves the row for metadata restore`() =
        runBlocking {
            val item = sampleRawProductItem(measurement = StashMeasurement.grams(500.0))
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
                    adjustment = StashMeasurementAdjustment.ChangeBy(StashMeasurement.grams(-500.0)),
                    action = ManualStashAction(reason = "Correction"),
                )

            val updated = assertIs<Result.Success<*, *>>(result).data as com.maksimowiczm.foodyou.stash.domain.entity.StashEntry
            assertEquals(StashMeasurement.grams(0.0), updated.measurement)
            assertEquals(1, repository.allItems().size)
            assertEquals(item.foodRef, repository.allItems().single().foodRef)
            assertEquals(StashMeasurement.grams(0.0), repository.allItems().single().measurement)
            val movement = repository.allMovements().single()
            assertEquals(StashMeasurement.grams(-500.0), movement.measurementChange)
            assertEquals("Correction", movement.note)
        }

    @Test
    fun `when manually adjusting anonymous dish item to zero, it deletes the item`() =
        runBlocking {
            val item = sampleAnonymousDishItem(measurement = StashMeasurement.servings(1.0))
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
                    adjustment = StashMeasurementAdjustment.ChangeBy(StashMeasurement.servings(-1.0)),
                    action = ManualStashAction(reason = "Finished"),
                )

            val updated = assertIs<Result.Success<*, *>>(result).data as com.maksimowiczm.foodyou.stash.domain.entity.StashEntry
            assertEquals(StashMeasurement.servings(0.0), updated.measurement)
            assertEquals(emptyList(), repository.allItems())
            val movement = repository.allMovements().single()
            assertEquals(StashMeasurement.servings(-1.0), movement.measurementChange)
            assertEquals("Finished", movement.note)
        }

    @Test
    fun `when removing product-backed item, it preserves a zero row and logs the full removal`() =
        runBlocking {
            val item = sampleRawProductItem(measurement = StashMeasurement.grams(250.0))
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

            val removed = assertIs<Result.Success<*, *>>(result).data as com.maksimowiczm.foodyou.stash.domain.entity.StashEntry
            assertEquals(0.0, removed.measurement.measurement.rawValue)
            assertEquals(1, repository.allItems().size)
            assertEquals(0.0, repository.allItems().single().measurement.measurement.rawValue)
            assertEquals(item.foodRef, repository.allItems().single().foodRef)
            val movement = repository.allMovements().single()
            assertEquals(-250.0, movement.measurementChange.measurement.rawValue)
            assertEquals("Discarded\nExpired yesterday", movement.note)
        }

    @Test
    fun `when moving item to another stash, it updates the stash id and records both sides of the move`() =
        runBlocking {
            val freezer = sampleStash(id = 1L, name = "Freezer")
            val pantry = sampleStash(id = 2L, name = "Pantry", ordering = 1)
            val item = sampleRawProductItem(stashId = freezer.id.value, measurement = StashMeasurement.grams(750.0))
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

            val moved = assertIs<Result.Success<*, *>>(result).data as com.maksimowiczm.foodyou.stash.domain.entity.StashEntry
            assertEquals(pantry.id, moved.stashId)
            assertEquals(
                listOf(
                    Triple(freezer.id, -750.0, "Moved to Pantry"),
                    Triple(pantry.id, 750.0, "Moved from Freezer"),
                ),
                repository.allMovements().map { Triple(it.stashId, it.measurementChange.measurement.rawValue, it.note) },
            )
        }

    @Test
    fun `when adjustment uses mismatched unit, it returns a unit mismatch error`() =
        runBlocking {
            val item = sampleRawProductItem(measurement = StashMeasurement.grams(500.0))
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
                    adjustment = StashMeasurementAdjustment.SetTo(StashMeasurement.servings(1.0)),
                    action = ManualStashAction(reason = "Correction"),
                )

            assertEquals(
                AdjustStashItemQuantityError.QuantityUnitMismatch(
                    expected = item.measurement.type,
                    actual = StashMeasurement.servings(1.0).type,
                ),
                assertIs<Result.Error<*, *>>(result).error,
            )
        }
}
