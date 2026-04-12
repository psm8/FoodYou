package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.stash.domain.entity.StashMovement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementId
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class LoadStashManagementStashesUseCaseTest {
    @Test
    fun `when loading stashes, it exposes item count and latest activity time`() =
        runBlocking {
            val stash = sampleStash(id = 1L, name = "Freezer")
            val item =
                sampleRawProductItem(id = 10L, stashId = stash.id.value).copy(
                    createdAt = LocalDateTime(2025, 1, 2, 9, 15),
                )
            val movement =
                StashMovement.new(
                    stashId = stash.id,
                    itemId = item.id,
                    operation = StashMovementOperation.ManualAdjust,
                    quantityChange = item.quantity,
                    linkedDiaryEntryId = null,
                    createdAt = LocalDateTime(2025, 1, 3, 18, 45),
                ).copy(id = StashMovementId(99))
            val repository =
                FakeStashRepository(
                    initialStashes = listOf(stash),
                    initialItems = listOf(item),
                    initialMovements = listOf(movement),
                )
            val useCase =
                LoadStashManagementStashesUseCase(
                    stashRepository = repository,
                    stashOwnerProvider = localOwnerProvider(),
                )

            val result = useCase.load()

            assertEquals(1, result.size)
            assertEquals("Freezer", result.single().name.value)
            assertEquals(1, result.single().itemCount)
            assertEquals(LocalDateTime(2025, 1, 3, 18, 45), result.single().lastModifiedAt)
        }
}
