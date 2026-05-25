package com.maksimowiczm.foodyou.app.ui.stash.browser

import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntryId
import com.maksimowiczm.foodyou.stash.domain.usecase.FIXED_NOW
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleRawProductItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDateTime

class StashBrowserStateTest {
    @Test
    fun `when filtering by name, it keeps only matching items`() {
        val state =
            browserState(
                items =
                    listOf(
                        item(name = "Apple juice"),
                        item(name = "Bread"),
                        item(name = "Plain yogurt"),
                    ),
            ).withQuery("bread")

        assertEquals(listOf("Bread"), state.visibleItems.map { it.name })
    }

    @Test
    fun `when sorting by recent, it returns newest items first`() {
        val state =
            browserState(
                items =
                    listOf(
                        item(id = 1L, name = "Old", createdAt = LocalDateTime(2024, 12, 31, 12, 0)),
                        item(id = 2L, name = "Newest", createdAt = LocalDateTime(2025, 1, 2, 12, 0)),
                        item(id = 3L, name = "Middle", createdAt = FIXED_NOW),
                    ),
            ).withSortOption(StashBrowserSortOption.DateAddedDescending)

        assertEquals(listOf("Newest", "Middle", "Old"), state.visibleItems.map { it.name })
    }

    @Test
    fun `when sorting by oldest date added, it returns oldest items first`() {
        val state =
            browserState(
                items =
                    listOf(
                        item(id = 1L, name = "Old", createdAt = LocalDateTime(2024, 12, 31, 12, 0)),
                        item(id = 2L, name = "Newest", createdAt = LocalDateTime(2025, 1, 2, 12, 0)),
                        item(id = 3L, name = "Middle", createdAt = FIXED_NOW),
                    ),
            ).withSortOption(StashBrowserSortOption.DateAddedAscending)

        assertEquals(listOf("Old", "Middle", "Newest"), state.visibleItems.map { it.name })
    }

    @Test
    fun `when sorting by name ascending, it orders items alphabetically`() {
        val state =
            browserState(
                items =
                    listOf(
                        item(name = "Banana"),
                        item(name = "apple"),
                        item(name = "Carrot"),
                    ),
            ).withSortOption(StashBrowserSortOption.NameAscending)

        assertEquals(listOf("apple", "Banana", "Carrot"), state.visibleItems.map { it.name })
    }

    @Test
    fun `when sorting by name descending, it orders items reverse alphabetically`() {
        val state =
            browserState(
                items =
                    listOf(
                        item(name = "Banana"),
                        item(name = "apple"),
                        item(name = "Carrot"),
                    ),
            ).withSortOption(StashBrowserSortOption.NameDescending)

        assertEquals(listOf("Carrot", "Banana", "apple"), state.visibleItems.map { it.name })
    }

    @Test
    fun `when sorting by quantity descending, it returns larger quantities first`() {
        val state =
            browserState(
                items =
                    listOf(
                        item(id = 1L, name = "Small", quantity = StashMeasurement.grams(3.0)),
                        item(id = 2L, name = "Large", quantity = StashMeasurement.grams(10.0)),
                        item(id = 3L, name = "Medium", quantity = StashMeasurement.grams(7.0)),
                    ),
            ).withSortOption(StashBrowserSortOption.QuantityDescending)

        assertEquals(listOf("Large", "Medium", "Small"), state.visibleItems.map { it.name })
    }

    @Test
    fun `when sorting by quantity ascending, it returns smaller quantities first`() {
        val state =
            browserState(
                items =
                    listOf(
                        item(id = 1L, name = "Small", quantity = StashMeasurement.grams(3.0)),
                        item(id = 2L, name = "Large", quantity = StashMeasurement.grams(10.0)),
                        item(id = 3L, name = "Medium", quantity = StashMeasurement.grams(7.0)),
                    ),
            ).withSortOption(StashBrowserSortOption.QuantityAscending)

        assertEquals(listOf("Small", "Medium", "Large"), state.visibleItems.map { it.name })
    }

    private fun browserState(items: List<StashBrowserItem>) =
        StashBrowserState(
            stashId = sampleRawProductItem().stashId,
            items = items,
        )

    private fun item(
        id: Long = 1L,
        name: String,
        quantity: StashMeasurement = StashMeasurement.grams(1.0),
        createdAt: kotlinx.datetime.LocalDateTime = FIXED_NOW,
    ): StashBrowserItem =
        StashBrowserItem(
            id = StashEntryId(id),
            name = name,
            isNameLoading = false,
            quantity = quantity,
            createdAt = createdAt,
            stashName = "Pantry",
            type = StashBrowserItemType.Product,
        )
}
