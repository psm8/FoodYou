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
    onFoodSelected: (StashAddDestination) -> Unit,
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
            onFoodSelected = onFoodSelected,
            onUpdateUsdaApiKey = onUpdateUsdaApiKey,
            onUpdateOpenFoodFactsCredentials = onUpdateOpenFoodFactsCredentials,
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        )
    }
}

@Composable
private fun SearchContent(
    paddingValues: PaddingValues,
    onFoodSelected: (StashAddDestination) -> Unit,
    onUpdateUsdaApiKey: () -> Unit,
    onUpdateOpenFoodFactsCredentials: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FoodSearchApp(
        onFoodClick = { food, measurement ->
            onFoodSelected(
                StashAddDestination.from(
                    foodId = food.id,
                    measurement = measurement,
                )
            )
        },
        onUpdateUsdaApiKey = onUpdateUsdaApiKey,
        onUpdateOpenFoodFactsCredentials = onUpdateOpenFoodFactsCredentials,
        modifier = modifier.padding(paddingValues).consumeWindowInsets(paddingValues),
    )
}

internal sealed interface StashAddDestination {
    data class Product(
        val productId: FoodId.Product,
        val measurement: Measurement,
    ) : StashAddDestination

    data class RecipeSnapshot(
        val recipeId: FoodId.Recipe,
    ) : StashAddDestination

    companion object {
        fun from(foodId: FoodId, measurement: Measurement): StashAddDestination =
            when (foodId) {
                is FoodId.Product -> Product(productId = foodId, measurement = measurement)
                is FoodId.Recipe -> RecipeSnapshot(recipeId = foodId)
            }
    }
}
