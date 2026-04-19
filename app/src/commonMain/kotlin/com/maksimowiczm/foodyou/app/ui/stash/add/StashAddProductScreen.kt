package com.maksimowiczm.foodyou.app.ui.stash.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.app.ui.food.component.MeasurementPicker
import com.maksimowiczm.foodyou.app.ui.food.component.rememberMeasurementPickerState
import com.maksimowiczm.foodyou.common.compose.extension.LaunchedCollectWithLifecycle
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import foodyou.app.generated.resources.Res
import foodyou.app.generated.resources.action_save
import foodyou.app.generated.resources.description_loading
import foodyou.app.generated.resources.error_product_not_found
import foodyou.app.generated.resources.headline_add_product_to_stash
import foodyou.app.generated.resources.message_home_stash_default_target
import foodyou.app.generated.resources.message_home_stash_select_stash
import foodyou.app.generated.resources.message_stash_add_invalid_measurement
import foodyou.app.generated.resources.message_stash_add_save_failed
import foodyou.app.generated.resources.title_measurement
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun StashAddProductScreen(
    productId: Long,
    preferredStashId: Long?,
    initialMeasurement: Measurement?,
    onBack: () -> Unit,
    onSaved: (StashDefinitionId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: StashAddProductViewModel =
        koinViewModel(
            parameters = {
                parametersOf(
                    FoodId.Product(productId),
                    preferredStashId?.let(::StashDefinitionId),
                    initialMeasurement,
                )
            }
        )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val latestOnSaved = rememberUpdatedState(onSaved)

    LaunchedCollectWithLifecycle(viewModel.events) { event ->
        when (event) {
            is StashAddProductEvent.Saved -> latestOnSaved.value(event.stashId)
        }
    }

    StashAddProductContent(
        state = state,
        onBack = onBack,
        onSave = viewModel::save,
        onSelectStash = viewModel::selectStash,
        modifier = modifier,
    )
}

@Composable
internal fun StashAddProductContent(
    state: StashAddProductState,
    onBack: () -> Unit,
    onSave: (Measurement) -> Unit,
    onSelectStash: (StashDefinitionId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurementState =
        key(state.productId, state.selectedMeasurement) {
            rememberMeasurementPickerState(
                suggestions = state.suggestions,
                possibleTypes = state.possibleMeasurementTypes,
                selectedMeasurement = state.selectedMeasurement,
            )
        }
    val canSave =
        !state.isLoading &&
            !state.isProductMissing &&
            measurementState.inputField.error == null &&
            (!state.requiresStashSelection || state.selectedStashId != null)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(Res.string.headline_add_product_to_stash)) },
                subtitle = {
                    Text(
                        when {
                            state.productName.isNotBlank() -> state.productName
                            state.isLoading -> stringResource(Res.string.description_loading)
                            else -> stringResource(Res.string.error_product_not_found)
                        }
                    )
                },
                navigationIcon = { ArrowBackIconButton(onBack) },
                actions = {
                    TextButton(
                        onClick = { onSave(measurementState.measurement) },
                        enabled = canSave,
                    ) {
                        Text(stringResource(Res.string.action_save))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(paddingValues)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.error == StashAddProductError.ProductNotFound) {
                Text(
                    text = stringResource(Res.string.error_product_not_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                return@Column
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.title_measurement),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    MeasurementPicker(state = measurementState)

                    state.error
                        ?.takeIf { it != StashAddProductError.ProductNotFound }
                        ?.let { error ->
                            Text(
                                text = error.message(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }

                    if (state.stashes.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.message_home_stash_default_target),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (state.requiresStashSelection) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(Res.string.message_home_stash_select_stash),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                state.stashes.forEach { stash ->
                                    FilterChip(
                                        selected = stash.id == state.selectedStashId,
                                        onClick = { onSelectStash(stash.id) },
                                        label = { Text(stash.name) },
                                    )
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
private fun StashAddProductError.message(): String =
    when (this) {
        StashAddProductError.ProductNotFound -> stringResource(Res.string.error_product_not_found)
        StashAddProductError.InvalidMeasurement ->
            stringResource(Res.string.message_stash_add_invalid_measurement)
        StashAddProductError.StashSelectionRequired ->
            stringResource(Res.string.message_home_stash_select_stash)
        StashAddProductError.SaveFailed -> stringResource(Res.string.message_stash_add_save_failed)
    }
