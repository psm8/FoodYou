package com.maksimowiczm.foodyou.app.ui.stash.add

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.app.ui.food.search.FoodSearchApp
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import foodyou.app.generated.resources.Res
import foodyou.app.generated.resources.headline_add_to_stash
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun StashAddSearchScreen(
    onBack: () -> Unit,
    onProductSelected: (FoodId.Product, Measurement) -> Unit,
    onRecipeSelected: (FoodId.Recipe) -> Unit,
    onUpdateUsdaApiKey: () -> Unit,
    onUpdateOpenFoodFactsCredentials: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(Res.string.headline_add_to_stash)) },
                navigationIcon = { ArrowBackIconButton(onBack) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        SearchContent(
            paddingValues = paddingValues,
            onProductSelected = onProductSelected,
            onRecipeSelected = onRecipeSelected,
            onUpdateUsdaApiKey = onUpdateUsdaApiKey,
            onUpdateOpenFoodFactsCredentials = onUpdateOpenFoodFactsCredentials,
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        )
    }
}

@Composable
private fun SearchContent(
    paddingValues: PaddingValues,
    onProductSelected: (FoodId.Product, Measurement) -> Unit,
    onRecipeSelected: (FoodId.Recipe) -> Unit,
    onUpdateUsdaApiKey: () -> Unit,
    onUpdateOpenFoodFactsCredentials: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FoodSearchApp(
        onFoodClick = { food, measurement ->
            when (val id = food.id) {
                is FoodId.Product -> onProductSelected(id, measurement)
                is FoodId.Recipe -> onRecipeSelected(id)
            }
        },
        onUpdateUsdaApiKey = onUpdateUsdaApiKey,
        onUpdateOpenFoodFactsCredentials = onUpdateOpenFoodFactsCredentials,
        modifier = modifier.padding(paddingValues).consumeWindowInsets(paddingValues),
    )
}
