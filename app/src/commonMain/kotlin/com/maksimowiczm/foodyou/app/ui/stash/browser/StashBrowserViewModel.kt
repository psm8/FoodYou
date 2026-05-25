package com.maksimowiczm.foodyou.app.ui.stash.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.app.ui.stash.StashFoodDisplay
import com.maksimowiczm.foodyou.app.ui.stash.observeStashFoodDisplay
import com.maksimowiczm.foodyou.app.ui.stash.toStashMeasurementOrNull
import com.maksimowiczm.foodyou.common.extension.combine
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope

internal class StashBrowserViewModel(
    private val stashId: StashDefinitionId,
    private val stashRepository: StashRepository,
    private val stashOwnerProvider: StashOwnerProvider,
    private val observeFoodUseCase: ObserveFoodUseCase,
    private val browserActions: StashBrowserActions,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope
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
                scope = scope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = "" to emptyList(),
            )

    private val stashEntries =
        stashRepository
            .observeStashContents(stashId)
            .onEach { isLoading.value = false }
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = emptyList(),
            )

    private val itemDisplays =
        stashEntries
            .flatMapLatest(::observeItemDisplays)
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = emptyList(),
            )
    val state: StateFlow<StashBrowserState> =
        combine(stashEntries, itemDisplays, stashDetails, query, sortOption, actionDialog, isLoading) { items, itemDisplays, stashDetails, query, sortOption, dialog, isLoading ->
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
            scope = scope,
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

    fun updateManualAdjustAmount(value: String) {
        actionDialog.update { (it as? StashBrowserActionDialog.ManualAdjust)?.copy(amount = value) ?: it }
    }

    fun updateManualAdjustMode(mode: StashBrowserAdjustMode) {
        actionDialog.update { dialog ->
            val adjust = dialog as? StashBrowserActionDialog.ManualAdjust ?: return@update dialog
            val resetAmount =
                when (mode) {
                    StashBrowserAdjustMode.SetTo -> adjust.item.quantity.measurement.rawValue.toString()
                    StashBrowserAdjustMode.ChangeBy -> ""
                }
            adjust.copy(mode = mode, amount = resetAmount)
        }
    }

    fun updateMoveTarget(targetStashId: StashDefinitionId?) {
        actionDialog.update { (it as? StashBrowserActionDialog.Move)?.copy(targetStashId = targetStashId) ?: it }
    }

    fun dismissActionDialog() {
        actionDialog.value = null
    }

    suspend fun confirmAction() {
        when (val dialog = actionDialog.value) {
            is StashBrowserActionDialog.Remove -> {
                browserActions.remove(itemId = dialog.item.id)
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
                )
                dismissActionDialog()
            }

            is StashBrowserActionDialog.Move -> {
                val target = dialog.targetStashId ?: return
                browserActions.move(
                    itemId = dialog.item.id,
                    targetStashId = target,
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

        return items.map { observeFoodUseCase.observeStashFoodDisplay(it.foodRef) }.combine()
    }
}