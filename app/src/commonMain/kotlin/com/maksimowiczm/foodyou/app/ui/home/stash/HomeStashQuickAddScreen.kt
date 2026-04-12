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
import com.maksimowiczm.foodyou.common.compose.extension.LaunchedCollectWithLifecycle
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantityUnit
import foodyou.app.generated.resources.Res
import foodyou.app.generated.resources.action_save
import foodyou.app.generated.resources.description_loading
import foodyou.app.generated.resources.error_product_not_found
import foodyou.app.generated.resources.headline_add_product_to_stash
import foodyou.app.generated.resources.label_home_stash_add_amount
import foodyou.app.generated.resources.label_home_stash_select
import foodyou.app.generated.resources.message_home_stash_default_target
import foodyou.app.generated.resources.message_home_stash_invalid_amount
import foodyou.app.generated.resources.message_home_stash_save_failed
import foodyou.app.generated.resources.message_home_stash_select_stash
import foodyou.app.generated.resources.unit_gram_short
import foodyou.app.generated.resources.unit_milliliter_short
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun HomeStashQuickAddScreen(
    productId: Long,
    preferredStashId: Long?,
    onBack: () -> Unit,
    onSaved: (StashDefinitionId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeStashQuickAddViewModel =
        koinViewModel(
            parameters = {
                parametersOf(
                    com.maksimowiczm.foodyou.food.domain.entity.FoodId.Product(productId),
                    preferredStashId?.let(::StashDefinitionId),
                )
            }
        )
    val state = viewModel.state.collectAsStateWithLifecycle().value

    val latestOnSaved = rememberUpdatedState(onSaved)
    LaunchedCollectWithLifecycle(viewModel.events) { event ->
        when (event) {
            is HomeStashQuickAddEvent.Saved -> latestOnSaved.value(event.stashId)
        }
    }

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
            if (state.error == HomeStashQuickAddError.ProductNotFound) {
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
                    OutlinedTextField(
                        value = state.amount,
                        onValueChange = viewModel::updateAmount,
                        label = {
                            Text(
                                stringResource(
                                    Res.string.label_home_stash_add_amount,
                                    state.unit.label(),
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = state.error == HomeStashQuickAddError.InvalidAmount,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )

                    if (state.error != null && state.error != HomeStashQuickAddError.ProductNotFound) {
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

@Composable
private fun HomeStashQuickAddError.message(): String =
    when (this) {
        HomeStashQuickAddError.ProductNotFound -> stringResource(Res.string.error_product_not_found)
        HomeStashQuickAddError.InvalidAmount -> stringResource(Res.string.message_home_stash_invalid_amount)
        HomeStashQuickAddError.StashSelectionRequired ->
            stringResource(Res.string.message_home_stash_select_stash)
        HomeStashQuickAddError.SaveFailed -> stringResource(Res.string.message_home_stash_save_failed)
    }
