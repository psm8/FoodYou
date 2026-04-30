package com.maksimowiczm.foodyou.app.ui.home.stash

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
import com.maksimowiczm.foodyou.app.ui.food.diary.quickadd.QuickAddForm
import com.maksimowiczm.foodyou.app.ui.food.diary.quickadd.rememberQuickAddFormState
import com.maksimowiczm.foodyou.common.compose.extension.LaunchedCollectWithLifecycle
import com.maksimowiczm.foodyou.common.domain.food.NutrientValue.Companion.toNutrientValue
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import foodyou.app.generated.resources.Res
import foodyou.app.generated.resources.action_save
import foodyou.app.generated.resources.description_home_stash_quick_add
import foodyou.app.generated.resources.headline_quick_add
import foodyou.app.generated.resources.label_home_stash_select
import foodyou.app.generated.resources.message_home_stash_default_target
import foodyou.app.generated.resources.message_home_stash_invalid_measurement
import foodyou.app.generated.resources.message_home_stash_quick_add_failed
import foodyou.app.generated.resources.message_home_stash_select_stash
import foodyou.app.generated.resources.title_measurement
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun HomeStashQuickAddScreen(
    preferredStashId: Long?,
    onBack: () -> Unit,
    onSaved: (StashDefinitionId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeStashQuickAddViewModel =
        koinViewModel(parameters = { parametersOf(preferredStashId?.let(::StashDefinitionId)) })
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val formState = rememberQuickAddFormState()
    val measurementState =
        key(Unit) {
            rememberMeasurementPickerState(
                suggestions = emptyList(),
                possibleTypes =
                    listOf(
                        MeasurementType.Gram,
                        MeasurementType.Ounce,
                        MeasurementType.Milliliter,
                        MeasurementType.FluidOunce,
                    ),
                selectedMeasurement = Measurement.Gram(Measurement.Gram.DEFAULT),
            )
        }

    val latestOnSaved = rememberUpdatedState(onSaved)
    LaunchedCollectWithLifecycle(viewModel.events) { event ->
        when (event) {
            is HomeStashQuickAddEvent.Saved -> latestOnSaved.value(event.stashId)
        }
    }

    val canSave =
        !state.isLoading &&
            formState.isValid &&
            measurementState.inputField.error == null &&
            (!state.requiresStashSelection || state.selectedStashId != null)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(Res.string.headline_quick_add)) },
                navigationIcon = { ArrowBackIconButton(onBack) },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.save(
                                name = formState.name.value,
                                nutritionFacts =
                                    NutritionFacts(
                                        energy = (formState.energy.value ?: 0.0).toNutrientValue(),
                                        proteins =
                                            (formState.proteins.value ?: 0.0).toNutrientValue(),
                                        carbohydrates =
                                            (formState.carbohydrates.value ?: 0.0)
                                                .toNutrientValue(),
                                        fats = (formState.fats.value ?: 0.0).toNutrientValue(),
                                    ),
                                measurement = measurementState.measurement,
                            )
                        },
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
            Text(
                text = stringResource(Res.string.description_home_stash_quick_add),
                style = MaterialTheme.typography.bodyMedium,
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    QuickAddForm(state = formState)

                    Text(
                        text = stringResource(Res.string.title_measurement),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    MeasurementPicker(state = measurementState)

                    state.error?.let { error ->
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
                                text = stringResource(Res.string.label_home_stash_select),
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
                                        onClick = { viewModel.selectStash(stash.id) },
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
private fun HomeStashQuickAddError.message(): String =
    when (this) {
        HomeStashQuickAddError.InvalidMeasurement ->
            stringResource(Res.string.message_home_stash_invalid_measurement)
        HomeStashQuickAddError.StashSelectionRequired ->
            stringResource(Res.string.message_home_stash_select_stash)
        HomeStashQuickAddError.SaveFailed ->
            stringResource(Res.string.message_home_stash_quick_add_failed)
    }
