package com.maksimowiczm.foodyou.app.ui.stash.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashItem
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantityUnit
import com.maksimowiczm.foodyou.stash.domain.repository.StashOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.StashQuantityAdjustment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

internal class StashBrowserViewModel(
    private val stashId: StashDefinitionId,
    private val stashRepository: StashRepository,
    private val stashOwnerProvider: StashOwnerProvider,
    private val browserActions: StashBrowserActions,
) : ViewModel() {
    private val ownerId = stashOwnerProvider.current()
    private val query = MutableStateFlow("")
    private val sortOption = MutableStateFlow(StashBrowserSortOption.DateAddedDescending)
    private val actionDialog = MutableStateFlow<StashBrowserActionDialog?>(null)
    private val isLoading = MutableStateFlow(true)

    private val stashDetails =
        stashRepository
            .observeStashes(ownerId)
            .map { stashes ->
                val currentStashName = stashes.firstOrNull { it.id == stashId }?.name?.value.orEmpty()
                val moveTargets =
                    stashes
                        .asSequence()
                        .filter { it.id != stashId }
                        .map { StashBrowserMoveTarget(id = it.id, name = it.name.value) }
                        .toList()
                currentStashName to moveTargets
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = "" to emptyList(),
            )

    private val items =
        stashRepository
            .observeStashContents(stashId)
            .map { stashItems ->
                isLoading.value = false
                stashItems
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = emptyList(),
            )

    val state: StateFlow<StashBrowserState> =
        combine(items, stashDetails, query, sortOption, actionDialog, isLoading) { values: Array<Any?> ->
            val items = values[0] as List<StashItem>
            val stashDetails = values[1] as Pair<String, List<StashBrowserMoveTarget>>
            val query = values[2] as String
            val sortOption = values[3] as StashBrowserSortOption
            val dialog = values[4] as StashBrowserActionDialog?
            val isLoading = values[5] as Boolean
            val stashName = stashDetails.first
            val moveTargets = stashDetails.second
            StashBrowserState(
                stashId = stashId,
                stashName = stashName,
                isLoading = isLoading,
                items = items.map { StashBrowserItem.from(item = it, stashName = stashName) },
                moveTargets = moveTargets,
                actionDialog = dialog,
                query = query,
                sortOption = sortOption,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(2_000),
            initialValue = StashBrowserState(stashId = stashId),
        )

    fun updateQuery(value: String) {
        query.value = value
    }

    fun updateSortOption(option: StashBrowserSortOption) {
        sortOption.value = option
    }

    fun requestRemove(item: StashBrowserItem) {
        actionDialog.value = state.value.showRemoveDialog(item).actionDialog
    }

    fun requestManualAdjust(item: StashBrowserItem) {
        actionDialog.value = state.value.showManualAdjustDialog(item).actionDialog
    }

    fun requestMove(item: StashBrowserItem) {
        actionDialog.value = state.value.showMoveDialog(item).actionDialog
    }

    fun updateRemoveReason(value: String) {
        actionDialog.update { (it as? StashBrowserActionDialog.Remove)?.copy(reason = value) ?: it }
    }

    fun updateRemoveNote(value: String) {
        actionDialog.update { (it as? StashBrowserActionDialog.Remove)?.copy(note = value) ?: it }
    }

    fun updateManualAdjustAmount(value: String) {
        actionDialog.update { (it as? StashBrowserActionDialog.ManualAdjust)?.copy(amount = value) ?: it }
    }

    fun updateManualAdjustReason(value: String) {
        actionDialog.update { (it as? StashBrowserActionDialog.ManualAdjust)?.copy(reason = value) ?: it }
    }

    fun updateManualAdjustNote(value: String) {
        actionDialog.update { (it as? StashBrowserActionDialog.ManualAdjust)?.copy(note = value) ?: it }
    }

    fun updateManualAdjustMode(mode: StashBrowserAdjustMode) {
        actionDialog.update { (it as? StashBrowserActionDialog.ManualAdjust)?.copy(mode = mode) ?: it }
    }

    fun updateMoveTarget(targetStashId: StashDefinitionId?) {
        actionDialog.update { (it as? StashBrowserActionDialog.Move)?.copy(targetStashId = targetStashId) ?: it }
    }

    fun updateMoveReason(value: String) {
        actionDialog.update { (it as? StashBrowserActionDialog.Move)?.copy(reason = value) ?: it }
    }

    fun updateMoveNote(value: String) {
        actionDialog.update { (it as? StashBrowserActionDialog.Move)?.copy(note = value) ?: it }
    }

    fun dismissActionDialog() {
        actionDialog.value = null
    }

    suspend fun confirmAction() {
        when (val dialog = actionDialog.value) {
            is StashBrowserActionDialog.Remove -> {
                browserActions.remove(
                    itemId = dialog.item.id,
                    action = dialog.toManualAction(),
                )
                dismissActionDialog()
            }

            is StashBrowserActionDialog.ManualAdjust -> {
                val quantity = dialog.item.quantityUnit.toQuantity(dialog.amount) ?: return
                val adjustment =
                    when (dialog.mode) {
                        StashBrowserAdjustMode.ChangeBy -> StashQuantityAdjustment.ChangeBy(quantity)
                        StashBrowserAdjustMode.SetTo -> StashQuantityAdjustment.SetTo(quantity)
                    }
                browserActions.adjust(
                    itemId = dialog.item.id,
                    adjustment = adjustment,
                    action = dialog.toManualAction(),
                )
                dismissActionDialog()
            }

            is StashBrowserActionDialog.Move -> {
                val target = dialog.targetStashId ?: return
                val targetName = state.value.moveTargets.firstOrNull { it.id == target }?.name
                browserActions.move(
                    itemId = dialog.item.id,
                    targetStashId = target,
                    action = dialog.toManualAction(targetStashName = targetName),
                )
                dismissActionDialog()
            }

            null -> Unit
        }
    }

    private val StashBrowserItem.quantityUnit: StashQuantityUnit
        get() = quantity.unit

    private fun StashQuantityUnit.toQuantity(amountText: String): StashQuantity? {
        val amount = amountText.toDoubleOrNull() ?: return null
        return when (this) {
            StashQuantityUnit.Gram -> StashQuantity.grams(amount)
            StashQuantityUnit.Milliliter -> StashQuantity.milliliters(amount)
            StashQuantityUnit.Fraction -> StashQuantity.fraction(amount)
        }
    }
}
