package com.maksimowiczm.foodyou.stash.domain.entity

import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.datetime.LocalDateTime

class StashItemTest {
    @Test
    fun `when quantity increases, returns signed adjustment in the same unit`() {
        val previous =
            stashItem(
                quantity = StashQuantity.grams(150.0),
            )
        val updated =
            previous.copy(
                quantity = StashQuantity.grams(225.0),
                snapshot = previous.snapshot,
            )

        val change = updated.quantityChangeFrom(previous)

        assertEquals(75.0, change.amount)
        assertEquals(StashQuantityUnit.Gram, change.unit)
    }

    @Test
    fun `when quantity units differ, quantity math fails fast`() {
        val grams = StashQuantity.grams(100.0)
        val fractions = StashQuantity.fraction(0.5)

        assertFailsWith<IllegalArgumentException> { grams - fractions }
    }
}

private fun stashItem(
    quantity: StashQuantity,
): StashItem =
    StashItem(
        id = StashItemId(1),
        stashId = StashDefinitionId(7),
        snapshot =
            RawProductSnapshot(
                productId = FoodId.Product(11),
                name = "Whole milk",
                brand = "Foodyou",
                barcode = "1234567890",
                note = "Fresh",
                isLiquid = true,
                packageWeight = 1000.0,
                servingWeight = 250.0,
                source = FoodSource(FoodSource.Type.User),
                nutritionFacts = NutritionFacts.Empty,
            ),
        quantity = quantity,
        createdAt = LocalDateTime(2025, 1, 1, 0, 0),
    )
