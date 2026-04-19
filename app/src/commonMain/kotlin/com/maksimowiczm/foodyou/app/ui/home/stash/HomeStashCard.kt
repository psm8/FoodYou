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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import foodyou.app.generated.resources.action_quick_add
import foodyou.app.generated.resources.action_view_stash
import foodyou.app.generated.resources.description_home_stash_stashes
import foodyou.app.generated.resources.description_home_stash_total_items
import foodyou.app.generated.resources.headline_stash
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun HomeStashCard(
    onConsumeItem: (StashItemId) -> Unit,
    onViewStash: (StashDefinitionId?) -> Unit,
    onAddToStash: (StashDefinitionId?) -> Unit,
    onQuickAddToStash: (StashDefinitionId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeStashCardViewModel = koinViewModel()
    val state = viewModel.state.collectAsStateWithLifecycle().value

    if (!state.isVisible) {
        return
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
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledIconButton(onClick = { onAddToStash(state.preferredStashId) }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(Res.string.action_add),
                        )
                    }
                    FilledTonalIconButton(onClick = { onQuickAddToStash(state.preferredStashId) }) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = stringResource(Res.string.action_quick_add),
                        )
                    }
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

@Composable private fun StashQuantity.formatForHomeCard(): String = displayLabel()
