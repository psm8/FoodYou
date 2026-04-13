package com.maksimowiczm.foodyou.app.ui.home.stash

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.home.shared.FoodYouHomeCard
import com.maksimowiczm.foodyou.app.ui.stash.displayLabel
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashItemId
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
import foodyou.app.generated.resources.Res
import foodyou.app.generated.resources.action_add
import foodyou.app.generated.resources.action_add_product_to_stash
import foodyou.app.generated.resources.action_add_recipe_snapshot_to_stash
import foodyou.app.generated.resources.action_view_stash
import foodyou.app.generated.resources.description_home_stash_total_items
import foodyou.app.generated.resources.description_home_stash_stashes
import foodyou.app.generated.resources.headline_stash
import foodyou.app.generated.resources.unit_gram_short
import foodyou.app.generated.resources.unit_milliliter_short
import foodyou.app.generated.resources.unit_stash_fraction_short
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun HomeStashCard(
    onConsumeItem: (StashItemId) -> Unit,
    onViewStash: (StashDefinitionId?) -> Unit,
    onAddProduct: (StashDefinitionId?) -> Unit,
    onAddRecipeSnapshot: (StashDefinitionId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeStashCardViewModel = koinViewModel()
    val state = viewModel.state.collectAsStateWithLifecycle().value
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    if (!state.isVisible) {
        return
    }

    if (showAddSheet) {
        val sheetState = rememberModalBottomSheetState()

        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = sheetState,
        ) {
            AddToStashSheet(
                onAddProduct = {
                    coroutineScope.launch {
                        sheetState.hide()
                        showAddSheet = false
                        onAddProduct(state.preferredStashId)
                    }
                },
                onAddRecipeSnapshot = {
                    coroutineScope.launch {
                        sheetState.hide()
                        showAddSheet = false
                        onAddRecipeSnapshot(state.preferredStashId)
                    }
                },
            )
        }
    }

    val totalItemsLabel =
        org.jetbrains.compose.resources.pluralStringResource(
            Res.plurals.description_home_stash_total_items,
            state.totalItemCount,
            state.totalItemCount,
        )

    FoodYouHomeCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(Res.string.headline_stash),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = totalItemsLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text =
                            stringResource(
                                Res.string.description_home_stash_stashes,
                                state.stashNames.joinToString(),
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledIconButton(onClick = { showAddSheet = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(Res.string.action_add),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.recentItems.forEach { item ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        ListItem(
                            modifier = Modifier.clickable { onConsumeItem(item.id) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(item.name) },
                            supportingContent = { Text(item.stashName) },
                            trailingContent = {
                                Text(
                                    text = item.quantity.formatForHomeCard(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            },
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onViewStash(state.preferredStashId) }) {
                    Text(stringResource(Res.string.action_view_stash))
                }
            }
        }
    }
}

@Composable
private fun StashQuantity.formatForHomeCard(): String = displayLabel()

@Composable
private fun AddToStashSheet(
    onAddProduct: () -> Unit,
    onAddRecipeSnapshot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ListItem(
            headlineContent = { Text(stringResource(Res.string.action_add_product_to_stash)) },
            modifier = Modifier.clickable { onAddProduct() },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        ListItem(
            headlineContent = { Text(stringResource(Res.string.action_add_recipe_snapshot_to_stash)) },
            modifier = Modifier.clickable { onAddRecipeSnapshot() },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}
