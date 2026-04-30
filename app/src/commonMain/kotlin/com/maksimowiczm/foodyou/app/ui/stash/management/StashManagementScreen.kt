package com.maksimowiczm.foodyou.app.ui.stash.management

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.app.ui.common.extension.hapticDraggableHandle
import com.maksimowiczm.foodyou.common.compose.extension.add
import com.maksimowiczm.foodyou.common.compose.utility.LocalDateFormatter
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import foodyou.app.generated.resources.*
import foodyou.app.generated.resources.Res
import foodyou.app.generated.resources.action_add_stash
import foodyou.app.generated.resources.action_delete_stash
import foodyou.app.generated.resources.action_rename_stash
import foodyou.app.generated.resources.action_reorder
import foodyou.app.generated.resources.action_save
import foodyou.app.generated.resources.description_delete_empty_stash
import foodyou.app.generated.resources.description_delete_non_empty_stash
import foodyou.app.generated.resources.description_stash_management
import foodyou.app.generated.resources.description_stash_management_empty
import foodyou.app.generated.resources.description_stash_management_many_items
import foodyou.app.generated.resources.description_stash_management_one_item
import foodyou.app.generated.resources.headline_create_stash
import foodyou.app.generated.resources.headline_delete_stash
import foodyou.app.generated.resources.headline_rename_stash
import foodyou.app.generated.resources.headline_stashes
import foodyou.app.generated.resources.placeholder_stash_name
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun StashManagementScreen(
    onBack: () -> Unit,
    onOpenStash: (StashDefinitionId) -> Unit,
    onAddToStash: (StashDefinitionId) -> Unit,
    onStartShoppingSession: (StashDefinitionId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: StashManagementViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    StashManagementScreen(
        state = state,
        onBack = onBack,
        onOpenStash = onOpenStash,
        onAddToStash = onAddToStash,
        onStartShoppingSession = onStartShoppingSession,
        onCreateStash = { name ->
            viewModel.updateDraft(name)
            viewModel.createStash()
        },
        onRenameStash = viewModel::renameStash,
        onDeleteStash = viewModel::deleteStash,
        onReorderStashes = viewModel::reorderStashes,
        modifier = modifier,
    )
}

@Composable
private fun StashManagementScreen(
    state: StashManagementUiState,
    onBack: () -> Unit,
    onOpenStash: (StashDefinitionId) -> Unit,
    onAddToStash: (StashDefinitionId) -> Unit,
    onStartShoppingSession: (StashDefinitionId) -> Unit,
    onCreateStash: (String) -> Unit,
    onRenameStash: (StashDefinitionId, String) -> Unit,
    onDeleteStash: (StashDefinitionId) -> Unit,
    onReorderStashes: (List<StashDefinitionId>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormatter = LocalDateFormatter.current
    val hapticFeedback = LocalHapticFeedback.current
    val existingNames =
        remember(state.stashes) { state.stashes.map(StashManagementItemUi::name).toSet() }

    var createDialogVisible by rememberSaveable { mutableStateOf(false) }
    var createName by rememberSaveable { mutableStateOf("") }
    var renameDialogState by remember { mutableStateOf<RenameDialogState?>(null) }
    var deleteDialogStashId by remember { mutableStateOf<StashDefinitionId?>(null) }
    var isReordering by rememberSaveable { mutableStateOf(false) }
    var orderedStashes by remember(state.sortedStashes) { mutableStateOf(state.sortedStashes) }

    val lazyListState = rememberLazyListState()
    val reorderableLazyListState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            orderedStashes = orderedStashes.reordered(from.index, to.index)
        }

    androidx.compose.runtime.LaunchedEffect(state.sortedStashes, isReordering) {
        if (!isReordering) {
            orderedStashes = state.sortedStashes
        }
    }

    val deleteTarget =
        deleteDialogStashId?.let { stashId -> state.stashes.firstOrNull { it.id == stashId } }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    if (createDialogVisible) {
        StashNameDialog(
            title = stringResource(Res.string.headline_create_stash),
            value = createName,
            onValueChange = { createName = it },
            confirmLabel = stringResource(Res.string.action_add_stash),
            canConfirm = canSaveStashName(createName, existingNames),
            onDismissRequest = {
                createDialogVisible = false
                createName = ""
            },
            onConfirm = {
                onCreateStash(createName)
                createDialogVisible = false
                createName = ""
            },
        )
    }

    renameDialogState?.let { renameState ->
        val currentStash = state.stashes.firstOrNull { it.id == renameState.stashId }
        if (currentStash != null) {
            StashNameDialog(
                title = stringResource(Res.string.headline_rename_stash),
                value = renameState.name,
                onValueChange = { renameDialogState = renameState.copy(name = it) },
                confirmLabel = stringResource(Res.string.action_rename_stash),
                canConfirm =
                    canSaveStashName(
                        value = renameState.name,
                        existingNames = existingNames,
                        currentName = currentStash.name,
                    ),
                onDismissRequest = { renameDialogState = null },
                onConfirm = {
                    onRenameStash(renameState.stashId, renameState.name)
                    renameDialogState = null
                },
            )
        }
    }

    deleteTarget?.let { stash ->
        DeleteStashDialog(
            stash = stash,
            onDismissRequest = { deleteDialogStashId = null },
            onDelete = {
                onDeleteStash(stash.id)
                deleteDialogStashId = null
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(Res.string.headline_stashes)) },
                subtitle = { Text(stringResource(Res.string.description_stash_management)) },
                navigationIcon = { ArrowBackIconButton(onBack) },
                actions = {
                    if (isReordering) {
                        IconButton(
                            onClick = {
                                onReorderStashes(orderedStashes.map(StashManagementItemUi::id))
                                isReordering = false
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = stringResource(Res.string.action_save),
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                createDialogVisible = true
                                createName = ""
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(Res.string.action_add_stash),
                            )
                        }

                        if (state.stashes.size > 1) {
                            IconButton(
                                onClick = {
                                    isReordering = true
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Reorder,
                                    contentDescription = stringResource(Res.string.action_reorder),
                                )
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = lazyListState,
            contentPadding = paddingValues.add(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!state.isLoading && state.stashes.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(stringResource(Res.string.description_stash_management_empty))
                        }
                    }
                }
            }

            items(items = orderedStashes, key = { it.id.value }) { stash ->
                ReorderableItem(state = reorderableLazyListState, key = stash.id.value) { isDragging
                    ->
                    StashManagementRow(
                        stash = stash,
                        isReordering = isReordering,
                        isDragging = isDragging,
                        lastModifiedLabel = dateFormatter.formatDateTime(stash.lastModifiedAt),
                        onAddToStash = { onAddToStash(stash.id) },
                        onOpen = { onOpenStash(stash.id) },
                        onStartShoppingSession = { onStartShoppingSession(stash.id) },
                        onRename = {
                            renameDialogState =
                                RenameDialogState(stashId = stash.id, name = stash.name)
                        },
                        onDelete = { deleteDialogStashId = stash.id },
                    )
                }
            }
        }
    }
}

context(_: ReorderableCollectionItemScope)
@Composable
private fun StashManagementRow(
    stash: StashManagementItemUi,
    isReordering: Boolean,
    isDragging: Boolean,
    lastModifiedLabel: String,
    onAddToStash: () -> Unit,
    onOpen: () -> Unit,
    onStartShoppingSession: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metadata =
        when (stash.itemCount) {
            1 -> stringResource(Res.string.description_stash_management_one_item, lastModifiedLabel)
            else ->
                stringResource(
                    Res.string.description_stash_management_many_items,
                    stash.itemCount,
                    lastModifiedLabel,
                )
        }

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        onClick = if (isReordering) ({}) else onOpen,
        shape = MaterialTheme.shapes.medium,
        color =
            if (isDragging) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
    ) {
        ListItem(
            headlineContent = { Text(stash.name) },
            supportingContent = { Text(metadata) },
            trailingContent = {
                Row {
                    if (isReordering) {
                        IconButton(
                            onClick = {},
                            modifier = Modifier.clearAndSetSemantics {}.hapticDraggableHandle(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.DragHandle,
                                contentDescription = stringResource(Res.string.action_reorder),
                            )
                        }
                    } else {
                        IconButton(onClick = onAddToStash) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(Res.string.action_add),
                            )
                        }
                        IconButton(onClick = onStartShoppingSession) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription =
                                    stringResource(Res.string.headline_stash_shopping_session),
                            )
                        }
                        IconButton(onClick = onRename) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(Res.string.action_rename_stash),
                            )
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(Res.string.action_delete_stash),
                            )
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun StashNameDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    confirmLabel: String,
    canConfirm: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                label = { Text(stringResource(Res.string.placeholder_stash_name)) },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = canConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

@Composable
private fun DeleteStashDialog(
    stash: StashManagementItemUi,
    onDismissRequest: () -> Unit,
    onDelete: () -> Unit,
) {
    val description =
        if (stash.itemCount == 0) {
            stringResource(Res.string.description_delete_empty_stash, stash.name)
        } else {
            stringResource(
                Res.string.description_delete_non_empty_stash,
                stash.name,
                stash.itemCount,
            )
        }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(Res.string.headline_delete_stash)) },
        text = { Text(description) },
        confirmButton = {
            TextButton(onClick = onDelete) { Text(stringResource(Res.string.action_delete_stash)) }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

private data class RenameDialogState(val stashId: StashDefinitionId, val name: String)
