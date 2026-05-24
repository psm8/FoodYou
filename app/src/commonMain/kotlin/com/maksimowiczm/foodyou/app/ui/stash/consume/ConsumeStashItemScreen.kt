package com.maksimowiczm.foodyou.app.ui.stash.consume

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.stash.displayLabel
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.app.ui.food.diary.component.ChipsDatePicker
import com.maksimowiczm.foodyou.app.ui.food.diary.component.rememberChipsDatePickerState
import com.maksimowiczm.foodyou.common.compose.extension.add
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.common.extension.minus
import com.maksimowiczm.foodyou.common.extension.plus
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import foodyou.app.generated.resources.*
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.collect
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ConsumeStashItemScreen(
    itemId: StashEntryId,
    onBack: () -> Unit,
    onConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ConsumeStashItemViewModel = koinViewModel(parameters = { parametersOf(itemId) })
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is ConsumeStashItemEvent.Consumed) {
                onConsumed()
            }
        }
    }

    ConsumeStashItemScreen(
        state = state,
        onBack = onBack,
        onAmountChange = viewModel::updateAmount,
        onMealSelected = viewModel::selectMeal,
        onDateSelected = viewModel::selectDate,
        onConsume = viewModel::consume,
        modifier = modifier,
    )
}

@Composable
internal fun ConsumeStashItemScreen(
    state: ConsumeStashItemState,
    onBack: () -> Unit,
    onAmountChange: (String) -> Unit,
    onMealSelected: (Long) -> Unit,
    onDateSelected: (kotlinx.datetime.LocalDate) -> Unit,
    onConsume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val dateState =
        rememberChipsDatePickerState(
            today = state.today,
            initialDates =
                listOf(
                    state.today.minus(1.days),
                    state.today,
                    state.today.plus(1.days),
                    state.selectedDate,
                ).distinct(),
            selectedDate = state.selectedDate,
        )

    LaunchedEffect(dateState.selectedDate) {
        onDateSelected(dateState.selectedDate)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        state.itemName.ifBlank {
                            stringResource(Res.string.action_consume_item)
                        }
                    )
                },
                navigationIcon = { ArrowBackIconButton(onBack) },
                actions = {
                    TextButton(onClick = onConsume, enabled = state.canSave) {
                        Text(stringResource(Res.string.action_consume_item))
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
                    .padding(paddingValues.add(horizontal = 16.dp, vertical = 16.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                state.isLoading -> {
                    Text(stringResource(Res.string.description_loading))
                }

                state.remainingQuantity == null -> {
                    Text(errorMessage(error = ConsumeStashItemError.ItemNotFound))
                }

                else -> {
                    state.error?.let { error ->
                        Text(
                            text = error.message(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    Text(
                        text =
                            stringResource(
                                Res.string.description_item_remaining_quantity,
                                state.remainingQuantity.label(),
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    OutlinedTextField(
                        value = state.amount,
                        onValueChange = onAmountChange,
                        label = { Text(stringResource(Res.string.label_action_amount)) },
                        supportingText = { Text(state.remainingQuantity.type.label()) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = state.error == ConsumeStashItemError.InvalidAmount,
                    )

                    Text(
                        text = stringResource(Res.string.headline_meals),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    if (state.meals.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.message_consume_item_no_meals),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.meals.forEach { meal ->
                                FilterChip(
                                    selected = meal.id == state.selectedMealId,
                                    onClick = { onMealSelected(meal.id) },
                                    label = { Text(meal.name) },
                                )
                            }
                        }
                    }

                    ChipsDatePicker(state = dateState)
                }
            }
        }
    }
}

@Composable
private fun ConsumeStashItemError.message(): String = errorMessage(this)

@Composable
private fun errorMessage(error: ConsumeStashItemError): String =
    when (error) {
        ConsumeStashItemError.ItemNotFound ->
            stringResource(Res.string.message_consume_item_item_not_found)
        ConsumeStashItemError.MealNotFound ->
            stringResource(Res.string.message_consume_item_no_meals)
        ConsumeStashItemError.InvalidAmount ->
            stringResource(Res.string.message_consume_item_invalid_amount)
        is ConsumeStashItemError.InsufficientQuantity ->
            stringResource(
                Res.string.message_consume_item_insufficient_quantity_detail,
                error.available.displayLabel(),
                error.requested.displayLabel(),
            )
        ConsumeStashItemError.Unknown -> stringResource(Res.string.message_consume_item_failed)
    }

@Composable
private fun StashMeasurement.label(): String = displayLabel()

@Composable
private fun MeasurementType.label(): String =
    when (this) {
        MeasurementType.Gram -> stringResource(Res.string.unit_gram_short)
        MeasurementType.Milliliter -> stringResource(Res.string.unit_milliliter_short)
        MeasurementType.Serving -> stringResource(Res.string.unit_stash_fraction_short)
        else -> stringResource(Res.string.unit_stash_fraction_short)
    }
