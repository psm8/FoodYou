package com.maksimowiczm.foodyou.app.ui.home.stash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.app.ui.food.shared.component.NutrientList
import com.maksimowiczm.foodyou.common.compose.extension.LaunchedCollectWithLifecycle
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import foodyou.app.generated.resources.Res
import foodyou.app.generated.resources.action_save
import foodyou.app.generated.resources.description_loading
import foodyou.app.generated.resources.error_recipe_not_found
import foodyou.app.generated.resources.headline_add_recipe_snapshot_to_stash
import foodyou.app.generated.resources.headline_home_stash_total_nutrition
import foodyou.app.generated.resources.label_home_stash_add_amount
import foodyou.app.generated.resources.label_home_stash_amount_unit
import foodyou.app.generated.resources.label_home_stash_select
import foodyou.app.generated.resources.label_home_stash_servings_made
import foodyou.app.generated.resources.message_home_stash_default_target
import foodyou.app.generated.resources.message_home_stash_invalid_amount
import foodyou.app.generated.resources.message_home_stash_invalid_servings
import foodyou.app.generated.resources.message_home_stash_save_snapshot_failed
import foodyou.app.generated.resources.message_home_stash_select_stash
import foodyou.app.generated.resources.unit_gram_short
import foodyou.app.generated.resources.unit_milliliter_short
import foodyou.app.generated.resources.unit_stash_fraction_short
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun HomeStashRecipeSnapshotScreen(
    recipeId: Long,
    preferredStashId: Long?,
    onBack: () -> Unit,
    onSaved: (StashDefinitionId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeStashRecipeSnapshotViewModel =
        koinViewModel(
            parameters = {
                parametersOf(
                    com.maksimowiczm.foodyou.food.domain.entity.FoodId.Recipe(recipeId),
                    preferredStashId?.let(::StashDefinitionId),
                )
            }
        )
    val state = viewModel.state.collectAsStateWithLifecycle().value

    val latestOnSaved = rememberUpdatedState(onSaved)
    LaunchedCollectWithLifecycle(viewModel.events) { event ->
        when (event) {
            is HomeStashRecipeSnapshotEvent.Saved -> latestOnSaved.value(event.stashId)
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(Res.string.headline_add_recipe_snapshot_to_stash)) },
                subtitle = {
                    Text(
                        when {
                            state.recipeName.isNotBlank() -> state.recipeName
                            state.isLoading -> stringResource(Res.string.description_loading)
                            else -> stringResource(Res.string.error_recipe_not_found)
                        }
                    )
                },
                navigationIcon = { ArrowBackIconButton(onBack) },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = state.canSave) {
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
            if (state.error == HomeStashRecipeSnapshotError.RecipeNotFound) {
                Text(
                    text = stringResource(Res.string.error_recipe_not_found),
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
                    if (state.availableUnits.size > 1) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(Res.string.label_home_stash_amount_unit),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                state.availableUnits.forEach { unit ->
                                    FilterChip(
                                        selected = unit == state.amountUnit,
                                        onClick = { viewModel.selectAmountUnit(unit) },
                                        label = { Text(unit.label()) },
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = state.totalAmount,
                        onValueChange = viewModel::updateTotalAmount,
                        label = {
                            Text(
                                stringResource(
                                    Res.string.label_home_stash_add_amount,
                                    state.amountUnit.label(),
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = state.error == HomeStashRecipeSnapshotError.InvalidAmount,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    OutlinedTextField(
                        value = state.servingsMade,
                        onValueChange = viewModel::updateServingsMade,
                        label = { Text(stringResource(Res.string.label_home_stash_servings_made)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = state.error == HomeStashRecipeSnapshotError.InvalidServings,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )

                    if (state.error != null && state.error != HomeStashRecipeSnapshotError.RecipeNotFound) {
                        Text(
                            text = state.error.message(),
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

            state.previewNutritionFacts?.let { facts ->
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
                            text = stringResource(Res.string.headline_home_stash_total_nutrition),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        NutrientList(facts = facts)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeStashRecipeSnapshotError.message(): String =
    when (this) {
        HomeStashRecipeSnapshotError.RecipeNotFound ->
            stringResource(Res.string.error_recipe_not_found)
        HomeStashRecipeSnapshotError.InvalidAmount ->
            stringResource(Res.string.message_home_stash_invalid_amount)
        HomeStashRecipeSnapshotError.InvalidServings ->
            stringResource(Res.string.message_home_stash_invalid_servings)
        HomeStashRecipeSnapshotError.StashSelectionRequired ->
            stringResource(Res.string.message_home_stash_select_stash)
        HomeStashRecipeSnapshotError.SaveFailed ->
            stringResource(Res.string.message_home_stash_save_snapshot_failed)
    }

@Composable
private fun MeasurementType.label(): String =
    when (this) {
        MeasurementType.Gram -> stringResource(Res.string.unit_gram_short)
        MeasurementType.Milliliter -> stringResource(Res.string.unit_milliliter_short)
        MeasurementType.Serving -> stringResource(Res.string.unit_stash_fraction_short)
        else -> stringResource(Res.string.unit_stash_fraction_short)
    }
