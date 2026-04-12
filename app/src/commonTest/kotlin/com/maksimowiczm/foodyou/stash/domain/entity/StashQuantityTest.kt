package com.maksimowiczm.foodyou.stash.domain.entity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StashQuantityTest {
    @Test
    fun when_adding_gram_quantities_returns_sum_in_grams() {
        val quantity = StashQuantity.grams(250.0) + StashQuantity.grams(50.0)

        assertEquals(StashQuantity.grams(300.0), quantity)
    }

    @Test
    fun when_subtracting_fraction_quantities_returns_remaining_fraction() {
        val quantity = StashQuantity.fraction(1.0) - StashQuantity.fraction(0.25)

        assertEquals(StashQuantity.fraction(0.75), quantity)
    }

    @Test
    fun when_combining_different_units_then_it_fails_fast() {
        assertFailsWith<IllegalArgumentException> {
            StashQuantity.grams(100.0) + StashQuantity.milliliters(100.0)
        }
    }
}
