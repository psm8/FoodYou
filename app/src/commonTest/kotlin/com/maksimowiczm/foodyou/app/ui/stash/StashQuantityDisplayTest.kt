package com.maksimowiczm.foodyou.app.ui.stash

import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantityUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StashQuantityDisplayTest {
    @Test
    fun `when grams exceed one thousand, it displays kilograms`() {
        assertEquals(
            StashQuantityDisplay(amount = 1.25, unit = StashQuantityDisplayUnit.Kilogram),
            StashQuantity.grams(1250.0).toDisplayQuantity(),
        )
    }

    @Test
    fun `when milliliters exceed one thousand, it displays liters`() {
        assertEquals(
            StashQuantityDisplay(amount = 1.5, unit = StashQuantityDisplayUnit.Liter),
            StashQuantity.milliliters(1500.0).toDisplayQuantity(),
        )
    }

    @Test
    fun `when signed parsing is enabled, negative quantities stay valid for manual adjust`() {
        assertEquals(
            StashQuantity.grams(-50.0),
            "-50".toStashQuantityOrNull(
                unit = StashQuantityUnit.Gram,
                requirePositive = false,
            ),
        )
    }

    @Test
    fun `when signed parsing is disabled, negative quantities stay invalid for add flows`() {
        assertNull("-50".toStashQuantityOrNull(unit = StashQuantityUnit.Gram))
    }
}
