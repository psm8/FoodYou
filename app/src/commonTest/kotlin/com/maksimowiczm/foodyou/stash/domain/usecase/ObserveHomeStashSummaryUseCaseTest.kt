package com.maksimowiczm.foodyou.stash.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDateTime

class ObserveHomeStashSummaryUseCaseTest {
    @Test
    fun `when all stashes are empty, summary is hidden`() {
        val summary =
            buildHomeStashSummary(
                stashes = listOf(sampleStash(id = 1L, name = "Fridge")),
                itemsByStash = listOf(emptyList()),
            )

        assertNull(summary)
    }

    @Test
    fun `when building summary, it counts all items and keeps five most recent ones`() {
        val fridge = sampleStash(id = 1L, name = "Fridge", ordering = 0)
        val pantry = sampleStash(id = 2L, name = "Pantry", ordering = 1)
        val fridgeItems =
            listOf(
                sampleRawProductItem(id = 1L, stashId = fridge.id.value).copy(
                    createdAt = LocalDateTime(2025, 1, 1, 8, 0)
                ),
                sampleRawProductItem(id = 2L, stashId = fridge.id.value).copy(
                    createdAt = LocalDateTime(2025, 1, 2, 8, 0)
                ),
                sampleRawProductItem(id = 3L, stashId = fridge.id.value).copy(
                    createdAt = LocalDateTime(2025, 1, 3, 8, 0)
                ),
            )
        val pantryItems =
            listOf(
                sampleRawProductItem(id = 4L, stashId = pantry.id.value).copy(
                    createdAt = LocalDateTime(2025, 1, 4, 8, 0)
                ),
                sampleRawProductItem(id = 5L, stashId = pantry.id.value).copy(
                    createdAt = LocalDateTime(2025, 1, 5, 8, 0)
                ),
                sampleRawProductItem(id = 6L, stashId = pantry.id.value).copy(
                    createdAt = LocalDateTime(2025, 1, 6, 8, 0)
                ),
            )

        val summary =
            buildHomeStashSummary(
                stashes = listOf(fridge, pantry),
                itemsByStash = listOf(fridgeItems, pantryItems),
            )

        requireNotNull(summary)
        assertEquals(6, summary.totalItemCount)
        assertEquals(listOf("Fridge", "Pantry"), summary.stashes.map { it.name })
        assertEquals(listOf(6L, 5L, 4L, 3L, 2L), summary.recentItems.map { it.id.value })
    }

    @Test
    fun `when exhausted items remain in a stash, they are ignored in the home summary`() {
        val fridge = sampleStash(id = 1L, name = "Fridge", ordering = 0)

        val summary =
            buildHomeStashSummary(
                stashes = listOf(fridge),
                itemsByStash =
                    listOf(
                        listOf(
                            sampleRawProductItem(id = 1L, stashId = fridge.id.value).copy(
                                measurement = com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement.grams(0.0),
                                createdAt = LocalDateTime(2025, 1, 1, 8, 0),
                            ),
                            sampleRawProductItem(id = 2L, stashId = fridge.id.value).copy(
                                createdAt = LocalDateTime(2025, 1, 2, 8, 0),
                            ),
                        )
                    ),
            )

        requireNotNull(summary)
        assertEquals(1, summary.totalItemCount)
        assertEquals(listOf(2L), summary.recentItems.map { it.id.value })
    }
}
