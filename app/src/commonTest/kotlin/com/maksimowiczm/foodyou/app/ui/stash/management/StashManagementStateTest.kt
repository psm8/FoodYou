package com.maksimowiczm.foodyou.app.ui.stash.management

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StashManagementStateTest {
    @Test
    fun `when normalizing stash name, it trims whitespace`() {
        assertEquals("Pantry", normalizeStashName("  Pantry  "))
    }

    @Test
    fun `when validating stash name, it rejects duplicates and blank values`() {
        assertFalse(canSaveStashName("   ", setOf("Freezer")))
        assertFalse(canSaveStashName("Freezer", setOf("Freezer")))
        assertTrue(canSaveStashName("Freezer", setOf("Fridge"), currentName = "Freezer"))
    }

    @Test
    fun `when validating stash name, it trims candidate and existing values before comparing`() {
        assertFalse(canSaveStashName("  Freezer  ", setOf(" Freezer ")))
        assertTrue(canSaveStashName("  Freezer  ", setOf(" Freezer "), currentName = "Freezer"))
    }

    @Test
    fun `when reordering stash items, it moves the requested row`() {
        val freezer =
            StashManagementItemUi(
                id = com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId(1),
                name = "Freezer",
                itemCount = 2,
                lastModifiedAt = LocalDateTime(2025, 1, 1, 12, 0),
                ordering = 0,
            )
        val fridge =
            StashManagementItemUi(
                id = com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId(2),
                name = "Fridge",
                itemCount = 1,
                lastModifiedAt = LocalDateTime(2025, 1, 2, 12, 0),
                ordering = 1,
            )
        val pantry =
            StashManagementItemUi(
                id = com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId(3),
                name = "Pantry",
                itemCount = 3,
                lastModifiedAt = LocalDateTime(2025, 1, 3, 12, 0),
                ordering = 2,
            )

        val reordered = listOf(freezer, fridge, pantry).reordered(fromIndex = 0, toIndex = 2)

        assertEquals(listOf(fridge, pantry, freezer), reordered)
    }

    @Test
    fun `when exposing sorted stashes, it orders them by ordering and id`() {
        val pantry =
            StashManagementItemUi(
                id = com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId(3),
                name = "Pantry",
                itemCount = 3,
                lastModifiedAt = LocalDateTime(2025, 1, 3, 12, 0),
                ordering = 1,
            )
        val freezer =
            StashManagementItemUi(
                id = com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId(1),
                name = "Freezer",
                itemCount = 2,
                lastModifiedAt = LocalDateTime(2025, 1, 1, 12, 0),
                ordering = 0,
            )
        val fridge =
            StashManagementItemUi(
                id = com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId(2),
                name = "Fridge",
                itemCount = 1,
                lastModifiedAt = LocalDateTime(2025, 1, 2, 12, 0),
                ordering = 1,
            )

        val sorted = StashManagementUiState(stashes = listOf(pantry, fridge, freezer)).sortedStashes

        assertEquals(listOf(freezer, fridge, pantry), sorted)
    }
}
