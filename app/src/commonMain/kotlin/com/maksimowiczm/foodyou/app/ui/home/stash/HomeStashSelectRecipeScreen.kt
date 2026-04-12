package com.maksimowiczm.foodyou.app.ui.home.stash

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.app.ui.food.search.FoodSearchApp
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import foodyou.app.generated.resources.Res
import foodyou.app.generated.resources.headline_home_stash_select_recipe
import foodyou.app.generated.resources.message_home_stash_recipe_required
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun HomeStashSelectRecipeScreen(
    onBack: () -> Unit,
    onRecipeSelected: (Long) -> Unit,
    onUpdateUsdaApiKey: () -> Unit,
    onUpdateOpenFoodFactsCredentials: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val recipeRequiredMessage = stringResource(Res.string.message_home_stash_recipe_required)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.headline_home_stash_select_recipe)) },
                navigationIcon = { ArrowBackIconButton(onBack) },
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Top),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues: PaddingValues ->
        FoodSearchApp(
            onFoodClick = { model, _ ->
                when (val id = model.id) {
                    is FoodId.Recipe -> onRecipeSelected(id.id)
                    is FoodId.Product ->
                        coroutineScope.launch { snackbarHostState.showSnackbar(recipeRequiredMessage) }
                }
            },
            onUpdateUsdaApiKey = onUpdateUsdaApiKey,
            onUpdateOpenFoodFactsCredentials = onUpdateOpenFoodFactsCredentials,
            modifier =
                Modifier.fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(paddingValues)
                    .consumeWindowInsets(paddingValues),
        )
    }
}
