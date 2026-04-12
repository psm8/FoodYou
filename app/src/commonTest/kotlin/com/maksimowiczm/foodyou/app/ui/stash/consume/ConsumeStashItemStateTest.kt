package com.maksimowiczm.foodyou.app.ui.stash.consume

import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class ConsumeStashItemStateTest {
    @Test
    fun `when amount matches the stash unit, it parses a consumable quantity`() {
        val state =
            ConsumeStashItemState(
                remainingQuantity = StashQuantity.grams(500.0),
                amount = "125.5",
                meals = listOf(ConsumeStashItemMeal(id = 1L, name = "Lunch")),
                selectedMealId = 1L,
                today = LocalDate(2025, 1, 1),
                selectedDate = LocalDate(2025, 1, 1),
                isLoading = false,
            )

        assertEquals(StashQuantity.grams(125.5), state.parsedAmount)
        assertTrue(state.canSave)
    }

    @Test
    fun `when amount is not numeric, it cannot be consumed`() {
        val state =
            ConsumeStashItemState(
                remainingQuantity = StashQuantity.fraction(1.0),
                amount = "half",
                meals = listOf(ConsumeStashItemMeal(id = 1L, name = "Dinner")),
                selectedMealId = 1L,
                today = LocalDate(2025, 1, 1),
                selectedDate = LocalDate(2025, 1, 1),
                isLoading = false,
            )

        assertEquals(null, state.parsedAmount)
        assertFalse(state.canSave)
    }
}
