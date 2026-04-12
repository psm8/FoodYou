package com.maksimowiczm.foodyou.app.ui.home.stash

import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeStashQuickAddStateTest {
    @Test
    fun `when multiple stashes exist without a selection, saving is disabled`() {
        val state =
            HomeStashQuickAddState(
                productId = FoodId.Product(1),
                isLoading = false,
                amount = "100",
                stashes =
                    listOf(
                        HomeStashQuickAddStash(id = StashDefinitionId(1), name = "Fridge"),
                        HomeStashQuickAddStash(id = StashDefinitionId(2), name = "Pantry"),
                    ),
            )

        assertTrue(state.requiresStashSelection)
        assertFalse(state.canSave)
    }

    @Test
    fun `when amount is not numeric, the quantity cannot be parsed`() {
        val state =
            HomeStashQuickAddState(
                productId = FoodId.Product(1),
                isLoading = false,
                amount = "a lot",
            )

        assertEquals(null, state.parsedQuantity)
        assertFalse(state.canSave)
    }
}
