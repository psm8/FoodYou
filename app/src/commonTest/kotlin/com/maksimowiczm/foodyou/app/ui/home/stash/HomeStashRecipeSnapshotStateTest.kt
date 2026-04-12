package com.maksimowiczm.foodyou.app.ui.home.stash

import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantityUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeStashRecipeSnapshotStateTest {
    @Test
    fun `when multiple stashes exist without a selection, saving is disabled`() {
        val state =
            HomeStashRecipeSnapshotState(
                recipeId = FoodId.Recipe(1),
                isLoading = false,
                totalAmount = "2",
                servingsMade = "4",
                stashes =
                    listOf(
                        HomeStashRecipeSnapshotStash(id = StashDefinitionId(1), name = "Fridge"),
                        HomeStashRecipeSnapshotStash(id = StashDefinitionId(2), name = "Pantry"),
                    ),
            )

        assertTrue(state.requiresStashSelection)
        assertFalse(state.canSave)
    }

    @Test
    fun `when servings are not numeric, servings cannot be parsed`() {
        val state =
            HomeStashRecipeSnapshotState(
                recipeId = FoodId.Recipe(1),
                isLoading = false,
                totalAmount = "2",
                servingsMade = "many",
            )

        assertEquals(null, state.parsedServings)
        assertFalse(state.canSave)
    }

    @Test
    fun `when gram unit is selected, amount is parsed as grams`() {
        val state =
            HomeStashRecipeSnapshotState(
                recipeId = FoodId.Recipe(1),
                isLoading = false,
                totalAmount = "250",
                servingsMade = "4",
                amountUnit = StashQuantityUnit.Gram,
                availableUnits = listOf(StashQuantityUnit.Fraction, StashQuantityUnit.Gram),
            )

        assertEquals(StashQuantity.grams(250.0), state.parsedQuantity)
        assertTrue(state.canSave)
    }
}
