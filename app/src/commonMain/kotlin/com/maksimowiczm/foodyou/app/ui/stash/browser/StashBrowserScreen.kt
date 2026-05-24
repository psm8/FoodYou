package com.maksimowiczm.foodyou.app.ui.stash.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.stash.displayLabel
import com.maksimowiczm.foodyou.app.ui.stash.toStashQuantityOrNull
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.common.compose.extension.add
import com.maksimowiczm.foodyou.common.compose.utility.LocalDateFormatter
import com.maksimowiczm.foodyou.common.compose.utility.formatClipZeros
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import foodyou.app.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun StashBrowserScreen(
    stashId: StashDefinitionId,
    onBack: () -> Unit,
    onConsumeItem: (StashEntryId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: StashBrowserViewModel = koinViewModel(parameters = { parametersOf(stashId) })
    val state = viewModel.state.collectAsStateWithLifecycle().value

    StashBrowserScreen(
        state = state,
        onBack = onBack,
        onQueryChange = viewModel::updateQuery,
        onSortChange = viewModel::updateSortOption,
        onConsumeItem = onConsumeItem,
        onRemoveItem = viewModel::requestRemove,
        onManualAdjustItem = viewModel::requestManualAdjust,
        onMoveItem = viewModel::requestMove,
        onRemoveReasonChange = viewModel::updateRemoveReason,
        onRemoveNoteChange = viewModel::updateRemoveNote,
        onAdjustAmountChange = viewModel::updateManualAdjustAmount,
        onAdjustReasonChange = viewModel::updateManualAdjustReason,
        onAdjustNoteChange = viewModel::updateManualAdjustNote,
        onAdjustModeChange = viewModel::updateManualAdjustMode,
        onMoveTargetChange = viewModel::updateMoveTarget,
        onMoveReasonChange = viewModel::updateMoveReason,
        onMoveNoteChange = viewModel::updateMoveNote,
        onDismissActionDialog = viewModel::dismissActionDialog,
        onConfirmAction = { viewModel.confirmAction() },
        modifier = modifier,
    )
}

@Composable
internal fun StashBrowserScreen(
    state: StashBrowserState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSortChange: (StashBrowserSortOption) -> Unit,
    onConsumeItem: (StashEntryId) -> Unit,
    onRemoveItem: (StashBrowserItem) -> Unit,
    onManualAdjustItem: (StashBrowserItem) -> Unit,
    onMoveItem: (StashBrowserItem) -> Unit,
    onRemoveReasonChange: (String) -> Unit,
    onRemoveNoteChange: (String) -> Unit,
    onAdjustAmountChange: (String) -> Unit,
    onAdjustReasonChange: (String) -> Unit,
    onAdjustNoteChange: (String) -> Unit,
    onAdjustModeChange: (StashBrowserAdjustMode) -> Unit,
    onMoveTargetChange: (StashDefinitionId?) -> Unit,
    onMoveReasonChange: (String) -> Unit,
    onMoveNoteChange: (String) -> Unit,
    onDismissActionDialog: () -> Unit,
    onConfirmAction: suspend () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormatter = LocalDateFormatter.current
    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    state.actionDialog?.let { dialog ->
        when (dialog) {
            is StashBrowserActionDialog.Remove ->
                RemoveItemDialog(
                    dialog = dialog,
                    onReasonChange = onRemoveReasonChange,
                    onNoteChange = onRemoveNoteChange,
                    onDismissRequest = onDismissActionDialog,
                    onConfirm = { coroutineScope.launch { onConfirmAction() } },
                )

            is StashBrowserActionDialog.ManualAdjust ->
                ManualAdjustDialog(
                    dialog = dialog,
                    onAmountChange = onAdjustAmountChange,
                    onReasonChange = onAdjustReasonChange,
                    onNoteChange = onAdjustNoteChange,
                    onModeChange = onAdjustModeChange,
                    onDismissRequest = onDismissActionDialog,
                    onConfirm = { coroutineScope.launch { onConfirmAction() } },
                )

            is StashBrowserActionDialog.Move ->
                MoveItemDialog(
                    dialog = dialog,
                    moveTargets = state.moveTargets,
                    onTargetChange = onMoveTargetChange,
                    onReasonChange = onMoveReasonChange,
                    onNoteChange = onMoveNoteChange,
                    onDismissRequest = onDismissActionDialog,
                    onConfirm = { coroutineScope.launch { onConfirmAction() } },
                )
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        state.stashName.ifBlank {
                            stringResource(Res.string.headline_stash_browser)
                        }
                    )
                },
                subtitle = { Text(stringResource(Res.string.description_stash_browser)) },
                navigationIcon = { ArrowBackIconButton(onBack) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = paddingValues.add(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        label = { Text(stringResource(Res.string.label_search_items)) },
                        placeholder = {
                            Text(stringResource(Res.string.placeholder_search_stash_items))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    Text(
                        text = stringResource(Res.string.label_sort_items),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StashBrowserSortOption.entries.forEach { option ->
                            FilterChip(
                                selected = option == state.sortOption,
                                onClick = { onSortChange(option) },
                                label = { Text(option.label()) },
                            )
                        }
                    }
                }
            }

            when {
                state.isLoading -> {
                    item {
                        MessageCard(
                            message = stringResource(Res.string.description_loading),
                        )
                    }
                }

                state.visibleItems.isEmpty() -> {
                    item {
                        MessageCard(
                            message =
                                if (state.query.isBlank()) {
                                    stringResource(Res.string.description_stash_browser_empty)
                                } else {
                                    stringResource(
                                        Res.string.description_stash_browser_filtered_empty,
                                        state.query,
                                    )
                                },
                        )
                    }
                }

                else -> {
                    items(state.visibleItems, key = { it.id.value }) { item ->
                        StashBrowserItemCard(
                            item = item,
                            dateLabel = dateFormatter.formatDateTime(item.createdAt),
                            onConsumeItem = onConsumeItem,
                            onRemoveItem = onRemoveItem,
                            onManualAdjustItem = onManualAdjustItem,
                            onMoveItem = onMoveItem,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StashBrowserItemCard(
    item: StashBrowserItem,
    dateLabel: String,
    onConsumeItem: (StashEntryId) -> Unit,
    onRemoveItem: (StashBrowserItem) -> Unit,
    onManualAdjustItem: (StashBrowserItem) -> Unit,
    onMoveItem: (StashBrowserItem) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                ItemTypeBadge(type = item.type)
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(Res.string.description_item_remaining_quantity, item.quantity.label()),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(Res.string.description_item_stash_name, item.stashName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(Res.string.description_item_added_on, dateLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onConsumeItem(item.id) }) {
                    Text(stringResource(Res.string.action_consume_item))
                }
                TextButton(onClick = { onManualAdjustItem(item) }) {
                    Text(stringResource(Res.string.action_manual_adjust_item))
                }
                TextButton(onClick = { onMoveItem(item) }) {
                    Text(stringResource(Res.string.action_move_item))
                }
                TextButton(onClick = { onRemoveItem(item) }) {
                    Text(stringResource(Res.string.action_remove_item))
                }
            }
        }
    }
}

@Composable
private fun ItemTypeBadge(type: StashBrowserItemType) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = type.badgeLabel(),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun RemoveItemDialog(
    dialog: StashBrowserActionDialog.Remove,
    onReasonChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(Res.string.headline_remove_item)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(Res.string.description_remove_item_confirmation, dialog.item.name),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = dialog.reason,
                    onValueChange = onReasonChange,
                    label = { Text(stringResource(Res.string.label_action_reason)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dialog.note,
                    onValueChange = onNoteChange,
                    label = { Text(stringResource(Res.string.label_action_note)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.action_remove_item))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

@Composable
private fun ManualAdjustDialog(
    dialog: StashBrowserActionDialog.ManualAdjust,
    onAmountChange: (String) -> Unit,
    onReasonChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onModeChange: (StashBrowserAdjustMode) -> Unit,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    val parsedQuantity = remember(dialog.amount, dialog.item.quantity.type) {
        dialog.item.quantity.type.toMeasurementOrNull(dialog.amount)
    }
    val canConfirm = parsedQuantity != null && dialog.reason.trim().isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(Res.string.headline_adjust_item)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = dialog.mode == StashBrowserAdjustMode.ChangeBy,
                        onClick = { onModeChange(StashBrowserAdjustMode.ChangeBy) },
                        label = { Text(stringResource(Res.string.label_action_mode_change_by)) },
                    )
                    FilterChip(
                        selected = dialog.mode == StashBrowserAdjustMode.SetTo,
                        onClick = { onModeChange(StashBrowserAdjustMode.SetTo) },
                        label = { Text(stringResource(Res.string.label_action_mode_set_to)) },
                    )
                }
                OutlinedTextField(
                    value = dialog.amount,
                    onValueChange = onAmountChange,
                    label = { Text(stringResource(Res.string.label_action_amount)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dialog.reason,
                    onValueChange = onReasonChange,
                    label = { Text(stringResource(Res.string.label_action_reason)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dialog.note,
                    onValueChange = onNoteChange,
                    label = { Text(stringResource(Res.string.label_action_note)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = canConfirm) {
                Text(stringResource(Res.string.action_save_manual_adjust))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

@Composable
private fun MoveItemDialog(
    dialog: StashBrowserActionDialog.Move,
    moveTargets: List<StashBrowserMoveTarget>,
    onTargetChange: (StashDefinitionId?) -> Unit,
    onReasonChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(Res.string.headline_move_item)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(Res.string.description_move_item_selection, dialog.item.name),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (moveTargets.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.description_no_move_targets),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        text = stringResource(Res.string.label_move_target),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        moveTargets.forEach { target ->
                            FilterChip(
                                selected = dialog.targetStashId == target.id,
                                onClick = { onTargetChange(target.id) },
                                label = { Text(target.name) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = dialog.reason,
                    onValueChange = onReasonChange,
                    label = { Text(stringResource(Res.string.label_action_reason)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dialog.note,
                    onValueChange = onNoteChange,
                    label = { Text(stringResource(Res.string.label_action_note)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = dialog.targetStashId != null) {
                Text(stringResource(Res.string.action_confirm_move_item))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

@Composable
private fun MessageCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(24.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun StashBrowserSortOption.label(): String =
    when (this) {
        StashBrowserSortOption.DateAddedDescending ->
            stringResource(Res.string.sort_date_added_descending)
        StashBrowserSortOption.DateAddedAscending ->
            stringResource(Res.string.sort_date_added_ascending)
        StashBrowserSortOption.NameAscending ->
            stringResource(Res.string.sort_name_ascending)
        StashBrowserSortOption.NameDescending ->
            stringResource(Res.string.sort_name_descending)
        StashBrowserSortOption.QuantityDescending ->
            stringResource(Res.string.sort_quantity_descending)
        StashBrowserSortOption.QuantityAscending ->
            stringResource(Res.string.sort_quantity_ascending)
    }

@Composable
private fun StashBrowserItemType.badgeLabel(): String =
    when (this) {
        StashBrowserItemType.Product -> stringResource(Res.string.badge_product)
        StashBrowserItemType.Dish -> stringResource(Res.string.badge_dish)
    }

@Composable
private fun StashMeasurement.label(): String = displayLabel()

private fun MeasurementType.toMeasurementOrNull(amountText: String): StashMeasurement? {
    return amountText.toStashQuantityOrNull(unit = this, requirePositive = false)
}
