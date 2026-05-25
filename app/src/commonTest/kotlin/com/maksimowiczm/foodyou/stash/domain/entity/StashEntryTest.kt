package com.maksimowiczm.foodyou.stash.domain.entity

import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.datetime.LocalDateTime

class StashEntryTest {
    @Test
    fun `when quantity increases, returns signed adjustment in the same unit`() {
        val previous =
            stashEntry(
                measurement = StashMeasurement.grams(150.0),
            )
        val updated =
            previous.copy(
                measurement = StashMeasurement.grams(225.0),
                foodRef = previous.foodRef,
            )

        val change = updated.quantityChangeFrom(previous)

        assertEquals(75.0, change.measurement.rawValue)
        assertEquals(MeasurementType.Gram, change.type)
    }

    @Test
    fun `when measurement types differ, quantity math fails fast`() {
        val grams = StashMeasurement.grams(100.0)
        val servings = StashMeasurement.servings(0.5)

        assertFailsWith<IllegalArgumentException> { grams - servings }
    }
}

private fun stashEntry(
    measurement: StashMeasurement,
): StashEntry =
    StashEntry(
        id = StashEntryId(1),
        stashId = StashDefinitionId(7),
        foodRef = StashFoodRef.Product(FoodId.Product(11)),
        measurement = measurement,
        createdAt = LocalDateTime(2025, 1, 1, 0, 0),
    )
