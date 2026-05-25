package com.maksimowiczm.foodyou.app.ui.stash.browser

import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.app.ui.stash.StashFoodDisplay
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntry
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.StashFoodRef
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import kotlinx.datetime.LocalDateTime

internal enum class StashBrowserSortOption {
    DateAddedDescending,
    DateAddedAscending,
    NameAscending,
    NameDescending,
    QuantityDescending,
    QuantityAscending,
}

internal enum class StashBrowserAdjustMode {
    ChangeBy,
    SetTo,
}

internal enum class StashBrowserItemType {
    Product,
    Dish,
}

internal data class StashBrowserItem(
    val id: StashEntryId,
    val name: String,
    val isNameLoading: Boolean,
    val quantity: StashMeasurement,
    val createdAt: LocalDateTime,
    val stashName: String,
    val type: StashBrowserItemType,
) {
    companion object {
        fun from(
            item: StashEntry,
            stashName: String,
            display: StashFoodDisplay = StashFoodDisplay.loading(),
        ): StashBrowserItem =
            StashBrowserItem(
                id = item.id,
                name = display.name,
                isNameLoading = display.isLoading,
                quantity = item.measurement,
                createdAt = item.createdAt,
                stashName = stashName,
                type =
                    when (item.foodRef) {
                        is StashFoodRef.Product -> StashBrowserItemType.Product
                        is StashFoodRef.Recipe -> StashBrowserItemType.Dish
                    },
            )
    }
}

internal data class StashBrowserMoveTarget(
    val id: StashDefinitionId,
    val name: String,
)

internal sealed interface StashBrowserActionDialog {
    data class Remove(
        val item: StashBrowserItem,
    ) : StashBrowserActionDialog

    data class ManualAdjust(
        val item: StashBrowserItem,
        val amount: String = "",
        val mode: StashBrowserAdjustMode = StashBrowserAdjustMode.SetTo,
    ) : StashBrowserActionDialog

    data class Move(
        val item: StashBrowserItem,
        val targetStashId: StashDefinitionId? = null,
    ) : StashBrowserActionDialog
}

internal data class StashBrowserState(
    val stashId: StashDefinitionId,
    val stashName: String = "",
    val isLoading: Boolean = true,
    val items: List<StashBrowserItem> = emptyList(),
    val moveTargets: List<StashBrowserMoveTarget> = emptyList(),
    val actionDialog: StashBrowserActionDialog? = null,
    val query: String = "",
    val sortOption: StashBrowserSortOption = StashBrowserSortOption.DateAddedDescending,
) {
    val visibleItems: List<StashBrowserItem>
        get() = items.filterByQuery(query).sortedWith(sortOption.comparator)

    fun withItems(items: List<StashBrowserItem>): StashBrowserState = copy(items = items)

    fun withMoveTargets(moveTargets: List<StashBrowserMoveTarget>): StashBrowserState = copy(moveTargets = moveTargets)

    fun withStashName(stashName: String): StashBrowserState = copy(stashName = stashName)

    fun withQuery(query: String): StashBrowserState = copy(query = query.trim())

    fun withSortOption(sortOption: StashBrowserSortOption): StashBrowserState = copy(sortOption = sortOption)

    fun showRemoveDialog(item: StashBrowserItem): StashBrowserState = copy(actionDialog = StashBrowserActionDialog.Remove(item))

    fun showManualAdjustDialog(item: StashBrowserItem): StashBrowserState =
        copy(actionDialog = StashBrowserActionDialog.ManualAdjust(item = item, amount = item.quantity.measurement.rawValue.toString()))

    fun showMoveDialog(item: StashBrowserItem): StashBrowserState =
        copy(actionDialog = StashBrowserActionDialog.Move(item = item, targetStashId = moveTargets.firstOrNull()?.id))

    fun dismissActionDialog(): StashBrowserState = copy(actionDialog = null)

    fun updateManualAdjustAmount(value: String): StashBrowserState =
        copy(actionDialog = actionDialog.updateIf<StashBrowserActionDialog.ManualAdjust> { copy(amount = value) })

    fun updateManualAdjustMode(mode: StashBrowserAdjustMode): StashBrowserState =
        copy(
            actionDialog = actionDialog.updateIf<StashBrowserActionDialog.ManualAdjust> {
                val resetAmount =
                    when (mode) {
                        StashBrowserAdjustMode.SetTo -> item.quantity.measurement.rawValue.toString()
                        StashBrowserAdjustMode.ChangeBy -> ""
                    }
                copy(mode = mode, amount = resetAmount)
            }
        )

    fun updateMoveTarget(targetStashId: StashDefinitionId?): StashBrowserState =
        copy(actionDialog = actionDialog.updateIf<StashBrowserActionDialog.Move> { copy(targetStashId = targetStashId) })

    private fun List<StashBrowserItem>.filterByQuery(query: String): List<StashBrowserItem> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            return this
        }

        return filter { item -> item.name.contains(normalizedQuery, ignoreCase = true) }
    }
}

internal val StashBrowserSortOption.comparator: Comparator<StashBrowserItem>
    get() =
        when (this) {
            StashBrowserSortOption.DateAddedDescending ->
                compareByDescending<StashBrowserItem> { it.createdAt }
                    .thenByDescending { it.id.value }

            StashBrowserSortOption.DateAddedAscending ->
                compareBy<StashBrowserItem> { it.createdAt }
                    .thenBy { it.id.value }

            StashBrowserSortOption.NameAscending ->
                compareBy<StashBrowserItem> { it.name.lowercase() }
                    .thenByDescending { it.createdAt }
                    .thenByDescending { it.id.value }

            StashBrowserSortOption.NameDescending ->
                compareByDescending<StashBrowserItem> { it.name.lowercase() }
                    .thenByDescending { it.createdAt }
                    .thenByDescending { it.id.value }

            StashBrowserSortOption.QuantityDescending ->
                compareByDescending<StashBrowserItem> { it.quantity.measurement.rawValue }
                    .thenBy { it.name.lowercase() }
                    .thenByDescending { it.createdAt }
                    .thenByDescending { it.id.value }

            StashBrowserSortOption.QuantityAscending ->
                compareBy<StashBrowserItem> { it.quantity.measurement.rawValue }
                    .thenBy { it.name.lowercase() }
                    .thenByDescending { it.createdAt }
                    .thenByDescending { it.id.value }
        }

private inline fun <reified T : StashBrowserActionDialog> StashBrowserActionDialog?.updateIf(transform: T.() -> T): StashBrowserActionDialog? {
    if (this !is T) return this
    return transform(this)
}