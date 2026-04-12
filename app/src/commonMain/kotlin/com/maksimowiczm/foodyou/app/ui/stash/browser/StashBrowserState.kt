package com.maksimowiczm.foodyou.app.ui.stash.browser

import com.maksimowiczm.foodyou.stash.domain.entity.AnonymousDishSnapshot
import com.maksimowiczm.foodyou.stash.domain.entity.RawProductSnapshot
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashItem
import com.maksimowiczm.foodyou.stash.domain.entity.StashItemId
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
import com.maksimowiczm.foodyou.stash.domain.usecase.ManualStashAction
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
    val id: StashItemId,
    val name: String,
    val quantity: StashQuantity,
    val createdAt: LocalDateTime,
    val stashName: String,
    val type: StashBrowserItemType,
) {
    companion object {
        fun from(item: StashItem, stashName: String): StashBrowserItem =
            StashBrowserItem(
                id = item.id,
                name = item.snapshot.name,
                quantity = item.quantity,
                createdAt = item.createdAt,
                stashName = stashName,
                type =
                    when (item.snapshot) {
                        is RawProductSnapshot -> StashBrowserItemType.Product
                        is AnonymousDishSnapshot -> StashBrowserItemType.Dish
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
        val reason: String = "",
        val note: String = "",
    ) : StashBrowserActionDialog {
        fun toManualAction(): ManualStashAction =
            ManualStashAction(
                reason = reason.normalizedOrDefault("Remove item"),
                note = note.trim(),
            )
    }

    data class ManualAdjust(
        val item: StashBrowserItem,
        val amount: String = "",
        val reason: String = "",
        val note: String = "",
        val mode: StashBrowserAdjustMode = StashBrowserAdjustMode.ChangeBy,
    ) : StashBrowserActionDialog {
        fun toManualAction(): ManualStashAction =
            ManualStashAction(
                reason = reason.normalizedOrDefault("Adjust item"),
                note = note.trim(),
            )
    }

    data class Move(
        val item: StashBrowserItem,
        val targetStashId: StashDefinitionId? = null,
        val reason: String = "",
        val note: String = "",
    ) : StashBrowserActionDialog {
        fun toManualAction(targetStashName: String?): ManualStashAction =
            ManualStashAction(
                reason =
                    reason.normalizedOrDefault(
                        targetStashName?.let { "Move to $it" } ?: "Move item"
                    ),
                note = note.trim(),
            )
    }

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
        copy(actionDialog = StashBrowserActionDialog.ManualAdjust(item = item, amount = item.quantity.amount.toString()))

    fun showMoveDialog(item: StashBrowserItem): StashBrowserState =
        copy(actionDialog = StashBrowserActionDialog.Move(item = item, targetStashId = moveTargets.firstOrNull()?.id))

    fun dismissActionDialog(): StashBrowserState = copy(actionDialog = null)

    fun updateRemoveReason(value: String): StashBrowserState =
        copy(actionDialog = actionDialog.updateIf<StashBrowserActionDialog.Remove> { copy(reason = value) })

    fun updateRemoveNote(value: String): StashBrowserState =
        copy(actionDialog = actionDialog.updateIf<StashBrowserActionDialog.Remove> { copy(note = value) })

    fun updateManualAdjustAmount(value: String): StashBrowserState =
        copy(actionDialog = actionDialog.updateIf<StashBrowserActionDialog.ManualAdjust> { copy(amount = value) })

    fun updateManualAdjustReason(value: String): StashBrowserState =
        copy(actionDialog = actionDialog.updateIf<StashBrowserActionDialog.ManualAdjust> { copy(reason = value) })

    fun updateManualAdjustNote(value: String): StashBrowserState =
        copy(actionDialog = actionDialog.updateIf<StashBrowserActionDialog.ManualAdjust> { copy(note = value) })

    fun updateManualAdjustMode(mode: StashBrowserAdjustMode): StashBrowserState =
        copy(actionDialog = actionDialog.updateIf<StashBrowserActionDialog.ManualAdjust> { copy(mode = mode) })

    fun updateMoveTarget(targetStashId: StashDefinitionId?): StashBrowserState =
        copy(actionDialog = actionDialog.updateIf<StashBrowserActionDialog.Move> { copy(targetStashId = targetStashId) })

    fun updateMoveReason(value: String): StashBrowserState =
        copy(actionDialog = actionDialog.updateIf<StashBrowserActionDialog.Move> { copy(reason = value) })

    fun updateMoveNote(value: String): StashBrowserState =
        copy(actionDialog = actionDialog.updateIf<StashBrowserActionDialog.Move> { copy(note = value) })

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
                compareByDescending<StashBrowserItem> { it.quantity.amount }
                    .thenBy { it.name.lowercase() }
                    .thenByDescending { it.createdAt }
                    .thenByDescending { it.id.value }

            StashBrowserSortOption.QuantityAscending ->
                compareBy<StashBrowserItem> { it.quantity.amount }
                    .thenBy { it.name.lowercase() }
                    .thenByDescending { it.createdAt }
                    .thenByDescending { it.id.value }
        }

private inline fun <reified T : StashBrowserActionDialog> StashBrowserActionDialog?.updateIf(transform: T.() -> T): StashBrowserActionDialog? {
    if (this !is T) return this
    return transform(this)
}

private fun String.normalizedOrDefault(default: String): String = trim().ifEmpty { default }
