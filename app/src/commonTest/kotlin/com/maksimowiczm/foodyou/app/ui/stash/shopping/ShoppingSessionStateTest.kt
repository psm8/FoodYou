package com.maksimowiczm.foodyou.app.ui.stash.shopping

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.food.search.domain.FoodSearch
import com.maksimowiczm.foodyou.stash.domain.entity.ShoppingSessionItem
import com.maksimowiczm.foodyou.stash.domain.entity.ShoppingSessionItemId
import com.maksimowiczm.foodyou.stash.domain.entity.ShoppingSessionProductDetails
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashFoodRef
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import com.maksimowiczm.foodyou.stash.domain.usecase.nutritionWithEnergy
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleProduct
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShoppingSessionStateTest {
    @Test
    fun `when stash product and measurement are selected, add is enabled`() {
        val state =
            ShoppingSessionUiState(
                isLoading = false,
                stashOptions =
                    listOf(ShoppingSessionStashOption(id = StashDefinitionId(1L), name = "Pantry")),
                selectedStashId = StashDefinitionId(1L),
                selectedProduct = selectedProduct(),
                pendingMeasurement = Measurement.Gram(250.0),
            )

        assertTrue(state.canAddPendingProduct)
    }

    @Test
    fun `when preview has items, confirm is enabled and calories are summed`() {
        val state =
            ShoppingSessionUiState(
                isLoading = false,
                stashOptions =
                    listOf(ShoppingSessionStashOption(id = StashDefinitionId(1L), name = "Pantry")),
                selectedStashId = StashDefinitionId(1L),
                items =
                    listOf(
                        previewItem(id = "1", energy = 60.0, measurementRawValue = 200.0),
                        previewItem(id = "2", energy = 130.0, measurementRawValue = 500.0),
                    ),
            )

        assertTrue(state.canConfirm)
        assertEquals(770.0, state.totalCalories)
    }

    @Test
    fun `when measurement is invalid, add stays disabled`() {
        val state =
            ShoppingSessionUiState(
                isLoading = false,
                stashOptions =
                    listOf(ShoppingSessionStashOption(id = StashDefinitionId(1L), name = "Pantry")),
                selectedStashId = StashDefinitionId(1L),
                selectedProduct = selectedProduct(),
                pendingMeasurement = null,
            )

        assertFalse(state.canAddPendingProduct)
    }

    @Test
    fun `when preview item measurement is invalid, confirm stays disabled`() {
        val state =
            ShoppingSessionUiState(
                isLoading = false,
                stashOptions =
                    listOf(ShoppingSessionStashOption(id = StashDefinitionId(1L), name = "Pantry")),
                selectedStashId = StashDefinitionId(1L),
                items =
                    listOf(
                        previewItem(id = "1", energy = 60.0, measurementRawValue = 200.0)
                            .updateQuantity("oops")
                    ),
            )

        assertFalse(state.canConfirm)
    }

    @Test
    fun `when editing package measurement, the original measurement type is preserved`() {
        val updated =
            ShoppingSessionListItem.from(
                    id = ShoppingSessionListItemId("1"),
                    sessionItem =
                        ShoppingSessionItem(
                            id = ShoppingSessionItemId("session-item-1"),
                            foodRef = StashFoodRef.Product(sampleProduct().id),
                            productDetails =
                                ShoppingSessionProductDetails.from(
                                    sampleProduct(packageWeight = 1000.0),
                                ),
                            measurement = StashMeasurement(Measurement.Package(1.5)),
                        ),
                )
                .updateQuantity("0.75")

        assertEquals(Measurement.Package(0.75), updated.measurement)
        assertEquals(StashMeasurement(Measurement.Package(0.75)), updated.sessionItem.measurement)
    }

    private fun selectedProduct(): ShoppingSessionSelectedProduct =
        ShoppingSessionSelectedProduct.from(
            FoodSearch.Product(
                id = sampleProduct().id,
                headline = sampleProduct().name,
                isLiquid = false,
                nutritionFacts = sampleProduct().nutritionFacts,
                totalWeight = sampleProduct().packageWeight,
                servingWeight = sampleProduct().servingWeight,
                suggestedMeasurement = Measurement.Gram(100.0),
            )
        )

    private fun previewItem(id: String, energy: Double, measurementRawValue: Double): ShoppingSessionListItem =
        ShoppingSessionListItem.from(
            id = ShoppingSessionListItemId(id),
            sessionItem =
                ShoppingSessionItem(
                    id = ShoppingSessionItemId("session-item-$id"),
                    foodRef = StashFoodRef.Product(sampleProduct().id),
                    productDetails =
                        ShoppingSessionProductDetails.from(
                            sampleProduct(nutritionFacts = nutritionWithEnergy(energy)),
                        ),
                    measurement = StashMeasurement(Measurement.Gram(measurementRawValue)),
                ),
        )
}
