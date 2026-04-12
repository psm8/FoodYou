package com.maksimowiczm.foodyou.app.ui.stash.shopping

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.food.search.domain.FoodSearch
import com.maksimowiczm.foodyou.stash.domain.entity.RawProductSnapshot
import com.maksimowiczm.foodyou.stash.domain.entity.ShoppingSessionItem
import com.maksimowiczm.foodyou.stash.domain.entity.ShoppingSessionItemId
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
import com.maksimowiczm.foodyou.stash.domain.usecase.nutritionWithEnergy
import com.maksimowiczm.foodyou.stash.domain.usecase.sampleProduct
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShoppingSessionStateTest {
    @Test
    fun `when stash product and quantity are selected, add is enabled`() {
        val state =
            ShoppingSessionUiState(
                isLoading = false,
                stashOptions = listOf(ShoppingSessionStashOption(id = StashDefinitionId(1L), name = "Pantry")),
                selectedStashId = StashDefinitionId(1L),
                selectedProduct = selectedProduct(),
                pendingQuantity = "250",
            )

        assertTrue(state.canAddPendingProduct)
    }

    @Test
    fun `when preview has items, confirm is enabled and calories are summed`() {
        val state =
            ShoppingSessionUiState(
                isLoading = false,
                stashOptions = listOf(ShoppingSessionStashOption(id = StashDefinitionId(1L), name = "Pantry")),
                selectedStashId = StashDefinitionId(1L),
                items =
                    listOf(
                        previewItem(id = "1", energy = 60.0, quantity = 200.0),
                        previewItem(id = "2", energy = 130.0, quantity = 500.0),
                    ),
            )

        assertTrue(state.canConfirm)
        assertEquals(770.0, state.totalCalories)
    }

    @Test
    fun `when quantity is invalid, add stays disabled`() {
        val state =
            ShoppingSessionUiState(
                isLoading = false,
                stashOptions = listOf(ShoppingSessionStashOption(id = StashDefinitionId(1L), name = "Pantry")),
                selectedStashId = StashDefinitionId(1L),
                selectedProduct = selectedProduct(),
                pendingQuantity = "lots",
            )

        assertFalse(state.canAddPendingProduct)
    }

    @Test
    fun `when preview item quantity is invalid, confirm stays disabled`() {
        val state =
            ShoppingSessionUiState(
                isLoading = false,
                stashOptions = listOf(ShoppingSessionStashOption(id = StashDefinitionId(1L), name = "Pantry")),
                selectedStashId = StashDefinitionId(1L),
                items = listOf(previewItem(id = "1", energy = 60.0, quantity = 200.0).updateQuantity("oops")),
            )

        assertFalse(state.canConfirm)
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

    private fun previewItem(
        id: String,
        energy: Double,
        quantity: Double,
    ): ShoppingSessionListItem =
        ShoppingSessionListItem.from(
            id = ShoppingSessionListItemId(id),
            sessionItem =
                ShoppingSessionItem(
                    id = ShoppingSessionItemId("session-item-$id"),
                    productId = sampleProduct().id,
                    snapshot = RawProductSnapshot.from(sampleProduct(nutritionFacts = nutritionWithEnergy(energy))),
                    quantity = StashQuantity.grams(quantity),
                ),
        )
}
