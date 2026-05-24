package com.maksimowiczm.foodyou.app.ui.stash.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.app.ui.stash.StashFoodDisplay
import com.maksimowiczm.foodyou.app.ui.stash.observeStashFoodDisplay
import com.maksimowiczm.foodyou.app.ui.stash.toStashMeasurementOrNull
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveFoodUseCase
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntry
import com.maksimowiczm.foodyou.stash.domain.repository.StashOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import com.maksimowiczm.foodyou.stash.domain.usecase.StashMeasurementAdjustment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

internal class StashBrowserViewModel(
    private val stashId: StashDefinitionId,
    private val stashRepository: StashRepository,
    private val stashOwnerProvider: StashOwnerProvider,
    private val observeFoodUseCase: ObserveFoodUseCase,
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

    private val stashEntries =
        stashRepository
            .observeStashContents(stashId)
            .onEach { isLoading.value = false }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = emptyList(),
            )

    private val itemDisplays =
        stashEntries
            .flatMapLatest(::observeItemDisplays)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = emptyList(),
            )
    val state: StateFlow<StashBrowserState> =
        combine(stashEntries, itemDisplays, stashDetails, query, sortOption, actionDialog, isLoading) { values: Array<Any?> ->
            val items = values[0] as List<StashEntry>
            val itemDisplays = values[1] as List<StashFoodDisplay>
            val stashDetails = values[2] as Pair<String, List<StashBrowserMoveTarget>>
            val query = values[3] as String
            val sortOption = values[4] as StashBrowserSortOption
            val dialog = values[5] as StashBrowserActionDialog?
            val isLoading = values[6] as Boolean
            val stashName = stashDetails.first
            val moveTargets = stashDetails.second
            StashBrowserState(
                stashId = stashId,
                stashName = stashName,
                isLoading = isLoading || itemDisplays.size < items.size || itemDisplays.any(StashFoodDisplay::isLoading),
                items =
                    items.mapIndexed { index, item ->
                        StashBrowserItem.from(
                            item = item,
                            stashName = stashName,
                            display = itemDisplays.getOrElse(index) { StashFoodDisplay.loading() },
                        )
                    },
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
                val quantity = dialog.item.quantityUnit.toMeasurement(dialog.amount) ?: return
                val adjustment =
                    when (dialog.mode) {
                        StashBrowserAdjustMode.ChangeBy -> StashMeasurementAdjustment.ChangeBy(quantity)
                        StashBrowserAdjustMode.SetTo -> StashMeasurementAdjustment.SetTo(quantity)
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

    private val StashBrowserItem.quantityUnit: MeasurementType
        get() = quantity.type

    private fun MeasurementType.toMeasurement(amountText: String) =
        amountText.toStashMeasurementOrNull(type = this, requirePositive = false)

    private fun observeItemDisplays(items: List<StashEntry>): Flow<List<StashFoodDisplay>> {
        if (items.isEmpty()) {
            return flowOf(emptyList())
        }

        return combine(items.map { observeFoodUseCase.observeStashFoodDisplay(it.foodRef) }) { displays ->
            displays.toList()
        }
    }
}
