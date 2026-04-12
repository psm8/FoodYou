package com.maksimowiczm.foodyou.app.ui.stash.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.app.ui.food.search.FoodSearchApp
import com.maksimowiczm.foodyou.common.compose.extension.LaunchedCollectWithLifecycle
import com.maksimowiczm.foodyou.common.compose.utility.formatClipZeros
import com.maksimowiczm.foodyou.food.search.domain.FoodSearch
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantityUnit
import foodyou.app.generated.resources.Res
import foodyou.app.generated.resources.action_add
import foodyou.app.generated.resources.action_confirm
import foodyou.app.generated.resources.action_remove_session_item
import foodyou.app.generated.resources.action_save
import foodyou.app.generated.resources.description_stash_shopping_empty
import foodyou.app.generated.resources.description_stash_shopping_search
import foodyou.app.generated.resources.headline_stash_shopping_preview
import foodyou.app.generated.resources.headline_stash_shopping_session
import foodyou.app.generated.resources.label_home_stash_select
import foodyou.app.generated.resources.label_stash_shopping_pending_quantity
import foodyou.app.generated.resources.label_stash_shopping_total_calories
import foodyou.app.generated.resources.message_home_stash_select_stash
import foodyou.app.generated.resources.message_stash_shopping_add_failed
import foodyou.app.generated.resources.message_stash_shopping_confirm_failed
import foodyou.app.generated.resources.message_stash_shopping_invalid_quantity
import foodyou.app.generated.resources.message_stash_shopping_select_product
import foodyou.app.generated.resources.message_stash_shopping_start_failed
import foodyou.app.generated.resources.unit_gram_short
import foodyou.app.generated.resources.unit_kcal
import foodyou.app.generated.resources.unit_milliliter_short
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun ShoppingSessionScreen(
    preferredStashId: Long?,
    onBack: () -> Unit,
    onFinished: (StashDefinitionId) -> Unit,
    onUpdateUsdaApiKey: () -> Unit,
    onUpdateOpenFoodFactsCredentials: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ShoppingSessionViewModel =
        koinViewModel(
            parameters = {
                parametersOf(preferredStashId?.let(::StashDefinitionId))
            }
        )
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val latestOnFinished = rememberUpdatedState(onFinished)
    val invalidFoodSelectionMessage = stringResource(Res.string.message_stash_shopping_select_product)

    LaunchedCollectWithLifecycle(viewModel.events) { event ->
        when (event) {
            is ShoppingSessionEvent.Finished -> latestOnFinished.value(event.stashId)
        }
    }

    val errorMessage =
        when (state.error) {
            ShoppingSessionUiError.StashSelectionRequired -> stringResource(Res.string.message_home_stash_select_stash)
            ShoppingSessionUiError.ProductSelectionRequired -> stringResource(Res.string.message_stash_shopping_select_product)
            ShoppingSessionUiError.InvalidQuantity -> stringResource(Res.string.message_stash_shopping_invalid_quantity)
            ShoppingSessionUiError.StartFailed -> stringResource(Res.string.message_stash_shopping_start_failed)
            ShoppingSessionUiError.AddFailed -> stringResource(Res.string.message_stash_shopping_add_failed)
            ShoppingSessionUiError.ConfirmFailed -> stringResource(Res.string.message_stash_shopping_confirm_failed)
            null -> null
        }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
        }
    }

    ShoppingSessionScreen(
        state = state,
        onBack = onBack,
        onSelectStash = viewModel::selectStash,
        onSelectProduct = viewModel::selectProduct,
        onPendingQuantityChange = viewModel::updatePendingQuantity,
        onAddPendingProduct = viewModel::addPendingProduct,
        onUpdateItemQuantity = viewModel::updateItemQuantity,
        onSaveItemQuantity = viewModel::saveItemQuantity,
        onRemoveItem = viewModel::removeItem,
        onConfirm = viewModel::confirm,
        onInvalidFoodSelection = {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(invalidFoodSelectionMessage)
            }
        },
        onUpdateUsdaApiKey = onUpdateUsdaApiKey,
        onUpdateOpenFoodFactsCredentials = onUpdateOpenFoodFactsCredentials,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@Composable
private fun ShoppingSessionScreen(
    state: ShoppingSessionUiState,
    onBack: () -> Unit,
    onSelectStash: (StashDefinitionId) -> Unit,
    onSelectProduct: (FoodSearch.Product) -> Unit,
    onPendingQuantityChange: (String) -> Unit,
    onAddPendingProduct: () -> Unit,
    onUpdateItemQuantity: (ShoppingSessionListItemId, String) -> Unit,
    onSaveItemQuantity: (ShoppingSessionListItemId) -> Unit,
    onRemoveItem: (ShoppingSessionListItemId) -> Unit,
    onConfirm: () -> Unit,
    onInvalidFoodSelection: () -> Unit,
    onUpdateUsdaApiKey: () -> Unit,
    onUpdateOpenFoodFactsCredentials: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(Res.string.headline_stash_shopping_session)) },
                subtitle = {
                    Text(
                        state.selectedStashName.ifBlank {
                            stringResource(Res.string.description_stash_shopping_search)
                        }
                    )
                },
                navigationIcon = { ArrowBackIconButton(onBack) },
                actions = {
                    TextButton(onClick = onConfirm, enabled = state.canConfirm) {
                        Text(stringResource(Res.string.action_confirm))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(paddingValues)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SessionControlCard(
                state = state,
                onSelectStash = onSelectStash,
                onPendingQuantityChange = onPendingQuantityChange,
                onAddPendingProduct = onAddPendingProduct,
            )

            PreviewCard(
                state = state,
                onUpdateItemQuantity = onUpdateItemQuantity,
                onSaveItemQuantity = onSaveItemQuantity,
                onRemoveItem = onRemoveItem,
            )

            FoodSearchApp(
                onFoodClick = { model, _ ->
                    when (model) {
                        is FoodSearch.Product -> onSelectProduct(model)
                        is FoodSearch.Recipe -> onInvalidFoodSelection()
                    }
                },
                onUpdateUsdaApiKey = onUpdateUsdaApiKey,
                onUpdateOpenFoodFactsCredentials = onUpdateOpenFoodFactsCredentials,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

@Composable
private fun SessionControlCard(
    state: ShoppingSessionUiState,
    onSelectStash: (StashDefinitionId) -> Unit,
    onPendingQuantityChange: (String) -> Unit,
    onAddPendingProduct: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.requiresStashSelection) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(Res.string.label_home_stash_select),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.stashOptions.forEach { stash ->
                            FilterChip(
                                selected = stash.id == state.selectedStashId,
                                onClick = { onSelectStash(stash.id) },
                                label = { Text(stash.name) },
                            )
                        }
                    }
                }
            }

            Text(
                text = state.selectedProduct?.name ?: stringResource(Res.string.message_stash_shopping_select_product),
                style = MaterialTheme.typography.titleMedium,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.pendingQuantity,
                    onValueChange = onPendingQuantityChange,
                    label = {
                        Text(
                            stringResource(
                                Res.string.label_stash_shopping_pending_quantity,
                                state.selectedProduct?.quantityUnit?.label().orEmpty(),
                            )
                        )
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                TextButton(onClick = onAddPendingProduct, enabled = state.canAddPendingProduct) {
                    Text(stringResource(Res.string.action_add))
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(
    state: ShoppingSessionUiState,
    onUpdateItemQuantity: (ShoppingSessionListItemId, String) -> Unit,
    onSaveItemQuantity: (ShoppingSessionListItemId) -> Unit,
    onRemoveItem: (ShoppingSessionListItemId) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.headline_stash_shopping_preview),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text =
                    stringResource(
                        Res.string.label_stash_shopping_total_calories,
                        state.totalCalories.formatClipZeros(),
                        stringResource(Res.string.unit_kcal),
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            if (state.items.isEmpty()) {
                Text(
                    text = stringResource(Res.string.description_stash_shopping_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.items, key = { it.id.value }) { item ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(item.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "${item.totalCalories.formatClipZeros()} ${stringResource(Res.string.unit_kcal)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = item.quantityText,
                                    onValueChange = { onUpdateItemQuantity(item.id, it) },
                                    label = {
                                        Text(
                                            stringResource(
                                                Res.string.label_stash_shopping_pending_quantity,
                                                item.quantityUnit.label(),
                                            )
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                )
                                TextButton(onClick = { onSaveItemQuantity(item.id) }) {
                                    Text(stringResource(Res.string.action_save))
                                }
                                TextButton(onClick = { onRemoveItem(item.id) }) {
                                    Text(stringResource(Res.string.action_remove_session_item))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StashQuantityUnit.label(): String =
    when (this) {
        StashQuantityUnit.Gram -> stringResource(Res.string.unit_gram_short)
        StashQuantityUnit.Milliliter -> stringResource(Res.string.unit_milliliter_short)
        StashQuantityUnit.Fraction -> ""
    }
