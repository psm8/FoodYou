package com.maksimowiczm.foodyou.app.infrastructure.android

import android.os.Bundle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.maksimowiczm.foodyou.app.ui.stash.add.StashAddProductContent
import com.maksimowiczm.foodyou.app.ui.stash.add.StashAddProductStash
import com.maksimowiczm.foodyou.app.ui.stash.add.StashAddProductState
import com.maksimowiczm.foodyou.app.ui.stash.browser.StashBrowserActionDialog
import com.maksimowiczm.foodyou.app.ui.stash.browser.StashBrowserItem
import com.maksimowiczm.foodyou.app.ui.stash.browser.StashBrowserItemType
import com.maksimowiczm.foodyou.app.ui.stash.browser.StashBrowserMoveTarget
import com.maksimowiczm.foodyou.app.ui.stash.browser.StashBrowserScreen
import com.maksimowiczm.foodyou.app.ui.stash.browser.StashBrowserState
import com.maksimowiczm.foodyou.app.ui.stash.consume.ConsumeStashItemMeal
import com.maksimowiczm.foodyou.app.ui.stash.consume.ConsumeStashItemScreen
import com.maksimowiczm.foodyou.app.ui.stash.consume.ConsumeStashItemState
import com.maksimowiczm.foodyou.app.ui.theme.FoodYouTheme
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

class DebugEvidenceActivity : FoodYouAbstractActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scenario =
            intent.getStringExtra(EXTRA_SCENARIO)?.let(Scenario::fromValue) ?: Scenario.AddProduct

        setContent {
            FoodYouTheme {
                Surface {
                    when (scenario) {
                        Scenario.AddProduct -> AddProductScenario()
                        Scenario.BrowseLiveRefs -> BrowserLiveRefsScenario()
                        Scenario.ConsumeProduct -> ConsumeProductScenario()
                        Scenario.RemoveRecipe -> RemoveRecipeScenario()
                    }
                }
            }
        }
    }

    @Composable
    private fun AddProductScenario() {
        StashAddProductContent(
            state =
                StashAddProductState(
                    productId = FoodId.Product(2L),
                    productName = "Greek yogurt",
                    isLoading = false,
                    suggestions = listOf(Measurement.Serving(1.0), Measurement.Gram(100.0)),
                    possibleMeasurementTypes =
                        listOf(
                            MeasurementType.Serving,
                            MeasurementType.Gram,
                            MeasurementType.Package,
                        ),
                    selectedMeasurement = Measurement.Serving(1.0),
                    stashes =
                        listOf(
                            StashAddProductStash(StashDefinitionId(1L), "Fridge"),
                            StashAddProductStash(StashDefinitionId(2L), "Pantry"),
                        ),
                    selectedStashId = StashDefinitionId(2L),
                ),
            onBack = {},
            onSave = {},
            onSelectStash = {},
        )
    }

    @Composable
    private fun BrowserLiveRefsScenario() {
        StashBrowserScreen(
            state = browserState(),
            onBack = {},
            onQueryChange = {},
            onSortChange = {},
            onConsumeItem = {},
            onRemoveItem = {},
            onManualAdjustItem = {},
            onMoveItem = {},
            onRemoveReasonChange = {},
            onRemoveNoteChange = {},
            onAdjustAmountChange = {},
            onAdjustReasonChange = {},
            onAdjustNoteChange = {},
            onAdjustModeChange = {},
            onMoveTargetChange = {},
            onMoveReasonChange = {},
            onMoveNoteChange = {},
            onDismissActionDialog = {},
            onConfirmAction = {},
        )
    }

    @Composable
    private fun ConsumeProductScenario() {
        ConsumeStashItemScreen(
            state =
                ConsumeStashItemState(
                    itemName = "Skyr (FoodYou)",
                    remainingQuantity = StashMeasurement.grams(450.0),
                    amount = "150",
                    meals =
                        listOf(
                            ConsumeStashItemMeal(id = 1L, name = "Breakfast"),
                            ConsumeStashItemMeal(id = 2L, name = "Snack"),
                        ),
                    selectedMealId = 1L,
                    today = LocalDate(2026, 5, 24),
                    selectedDate = LocalDate(2026, 5, 24),
                    isLoading = false,
                ),
            onBack = {},
            onAmountChange = {},
            onMealSelected = {},
            onDateSelected = {},
            onConsume = {},
        )
    }

    @Composable
    private fun RemoveRecipeScenario() {
        val recipeItem = sampleRecipeItem()
        StashBrowserScreen(
            state =
                browserState().copy(
                    actionDialog =
                        StashBrowserActionDialog.Remove(
                            item = recipeItem,
                            reason = "Spoiled batch",
                            note = "Removed the leftover recipe entry from the fridge stash.",
                        )
                ),
            onBack = {},
            onQueryChange = {},
            onSortChange = {},
            onConsumeItem = {},
            onRemoveItem = {},
            onManualAdjustItem = {},
            onMoveItem = {},
            onRemoveReasonChange = {},
            onRemoveNoteChange = {},
            onAdjustAmountChange = {},
            onAdjustReasonChange = {},
            onAdjustNoteChange = {},
            onAdjustModeChange = {},
            onMoveTargetChange = {},
            onMoveReasonChange = {},
            onMoveNoteChange = {},
            onDismissActionDialog = {},
            onConfirmAction = {},
        )
    }

    private fun browserState() =
        StashBrowserState(
            stashId = StashDefinitionId(1L),
            stashName = "Fridge",
            isLoading = false,
            items = listOf(sampleRecipeItem(), sampleProductItem()),
            moveTargets = listOf(StashBrowserMoveTarget(id = StashDefinitionId(2L), name = "Pantry")),
        )

    private fun sampleProductItem() =
        StashBrowserItem(
            id = StashEntryId(1L),
            name = "Skyr (FoodYou)",
            isNameLoading = false,
            quantity = StashMeasurement.grams(450.0),
            createdAt = LocalDateTime(2026, 5, 24, 8, 30),
            stashName = "Fridge",
            type = StashBrowserItemType.Product,
        )

    private fun sampleRecipeItem() =
        StashBrowserItem(
            id = StashEntryId(2L),
            name = "Pizza",
            isNameLoading = false,
            quantity = StashMeasurement.packages(0.5),
            createdAt = LocalDateTime(2026, 5, 24, 8, 15),
            stashName = "Fridge",
            type = StashBrowserItemType.Dish,
        )

    private enum class Scenario(val value: String) {
        AddProduct("stash_add_product"),
        BrowseLiveRefs("stash_browser_live_refs"),
        ConsumeProduct("consume_stash_product"),
        RemoveRecipe("stash_remove_recipe");

        companion object {
            fun fromValue(value: String): Scenario? = entries.firstOrNull { it.value == value }
        }
    }

    companion object {
        const val EXTRA_SCENARIO = "scenario"
    }
}
