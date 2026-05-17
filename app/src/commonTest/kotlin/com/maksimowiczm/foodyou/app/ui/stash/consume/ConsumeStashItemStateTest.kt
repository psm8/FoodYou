package com.maksimowiczm.foodyou.app.ui.stash.consume

import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
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
                remainingQuantity = StashMeasurement.grams(500.0),
                amount = "125.5",
                meals = listOf(ConsumeStashItemMeal(id = 1L, name = "Lunch")),
                selectedMealId = 1L,
                today = LocalDate(2025, 1, 1),
                selectedDate = LocalDate(2025, 1, 1),
                isLoading = false,
            )

        assertEquals(StashMeasurement.grams(125.5), state.parsedAmount)
        assertTrue(state.canSave)
    }

    @Test
    fun `when amount is a named fraction, it parses to a decimal quantity`() {
        val state =
            ConsumeStashItemState(
                remainingQuantity = StashMeasurement.servings(1.0),
                amount = "half",
                meals = listOf(ConsumeStashItemMeal(id = 1L, name = "Dinner")),
                selectedMealId = 1L,
                today = LocalDate(2025, 1, 1),
                selectedDate = LocalDate(2025, 1, 1),
                isLoading = false,
            )

        assertEquals(StashMeasurement.servings(0.5), state.parsedAmount)
        assertTrue(state.canSave)
    }

    @Test
    fun `when amount is a slash fraction, it parses to a decimal quantity`() {
        val state =
            ConsumeStashItemState(
                remainingQuantity = StashMeasurement.grams(500.0),
                amount = "1/4",
                meals = listOf(ConsumeStashItemMeal(id = 1L, name = "Dinner")),
                selectedMealId = 1L,
                today = LocalDate(2025, 1, 1),
                selectedDate = LocalDate(2025, 1, 1),
                isLoading = false,
            )

        assertEquals(StashMeasurement.grams(0.25), state.parsedAmount)
        assertTrue(state.canSave)
    }

    @Test
    fun `when amount is above the remaining quantity, it can still be submitted for domain validation`() {
        val state =
            ConsumeStashItemState(
                remainingQuantity = StashMeasurement.grams(50.0),
                amount = "100",
                meals = listOf(ConsumeStashItemMeal(id = 1L, name = "Dinner")),
                selectedMealId = 1L,
                today = LocalDate(2025, 1, 1),
                selectedDate = LocalDate(2025, 1, 1),
                isLoading = false,
            )

        assertEquals(StashMeasurement.grams(100.0), state.parsedAmount)
        assertTrue(state.canSave)
    }

    @Test
    fun `when amount is an invalid fraction, it cannot be consumed`() {
        val state =
            ConsumeStashItemState(
                remainingQuantity = StashMeasurement.servings(1.0),
                amount = "1/0",
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
