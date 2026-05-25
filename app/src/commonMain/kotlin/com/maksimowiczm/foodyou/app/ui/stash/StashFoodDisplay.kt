package com.maksimowiczm.foodyou.app.ui.stash

import com.maksimowiczm.foodyou.food.domain.entity.Food
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveFoodUseCase
import com.maksimowiczm.foodyou.stash.domain.entity.StashFoodRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

internal data class StashFoodDisplay(
    val name: String,
    val isLoading: Boolean,
) {
    companion object {
        fun loading(): StashFoodDisplay = StashFoodDisplay(name = "", isLoading = true)
    }
}

internal fun ObserveFoodUseCase.observeStashFoodDisplay(foodRef: StashFoodRef): Flow<StashFoodDisplay> =
    observe(foodRef.foodId())
        .map { food ->
            food?.toStashFoodDisplay() ?: StashFoodDisplay(name = foodRef.unavailableName(), isLoading = false)
        }.onStart { emit(StashFoodDisplay.loading()) }

private fun Food.toStashFoodDisplay(): StashFoodDisplay = StashFoodDisplay(name = headline, isLoading = false)

private fun StashFoodRef.foodId(): FoodId =
    when (this) {
        is StashFoodRef.Product -> productId
        is StashFoodRef.Recipe -> recipeId
    }

private fun StashFoodRef.unavailableName(): String =
    when (this) {
        is StashFoodRef.Product -> "Unavailable product"
        is StashFoodRef.Recipe -> "Unavailable recipe"
    }
