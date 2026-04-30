package com.maksimowiczm.foodyou.app.ui.home.stash

import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeStashQuickAddStateTest {
    @Test
    fun `when multiple stashes exist without a selection then explicit selection is required`() {
        val state =
            HomeStashQuickAddState(
                isLoading = false,
                stashes =
                    listOf(
                        HomeStashQuickAddStash(id = StashDefinitionId(1), name = "Fridge"),
                        HomeStashQuickAddStash(id = StashDefinitionId(2), name = "Pantry"),
                    ),
            )

        assertTrue(state.requiresStashSelection)
    }

    @Test
    fun `when one stash exists then explicit selection is not required`() {
        val state =
            HomeStashQuickAddState(
                isLoading = false,
                stashes = listOf(HomeStashQuickAddStash(id = StashDefinitionId(1), name = "Fridge")),
            )

        assertFalse(state.requiresStashSelection)
    }
}
