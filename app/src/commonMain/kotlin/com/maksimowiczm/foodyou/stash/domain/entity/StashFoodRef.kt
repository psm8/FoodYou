package com.maksimowiczm.foodyou.stash.domain.entity

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.common.domain.measurement.type
import com.maksimowiczm.foodyou.food.domain.entity.FoodId

sealed interface StashFoodRef {
    data class Product(val productId: FoodId.Product) : StashFoodRef

    data class Recipe(
        val recipeId: FoodId.Recipe,
        val totalWeight: Double,
        val totalAmount: Measurement,
    ) : StashFoodRef {
        init {
            require(totalWeight >= 0.0) { "Recipe total weight must not be negative" }
            require(totalAmount.rawValue > 0.0) { "Recipe total amount must be greater than 0" }
        }

        companion object {
            fun from(recipe: com.maksimowiczm.foodyou.food.domain.entity.Recipe, totalAmount: Measurement): Recipe =
                from(recipe = recipe, totalAmount = totalAmount, servingsMade = recipe.servings)

            fun from(
                recipe: com.maksimowiczm.foodyou.food.domain.entity.Recipe,
                totalAmount: Measurement,
                servingsMade: Int,
            ): Recipe {
                require(servingsMade > 0) { "Recipe servings must be greater than 0" }
                return Recipe(
                    recipeId = recipe.id,
                    totalWeight = totalWeightOf(recipe, totalAmount, servingsMade),
                    totalAmount = totalAmount,
                )
            }

            private fun totalWeightOf(
                recipe: com.maksimowiczm.foodyou.food.domain.entity.Recipe,
                totalAmount: Measurement,
                servingsMade: Int,
            ): Double =
                when (totalAmount.type) {
                    MeasurementType.Serving,
                    MeasurementType.Package -> {
                        val batchMultiplier = servingsMade.toDouble() / recipe.servings.toDouble()
                        recipe.totalWeight * batchMultiplier
                    }

                    MeasurementType.Gram,
                    MeasurementType.Milliliter,
                    MeasurementType.Ounce,
                    MeasurementType.FluidOunce -> totalAmount.rawValue
                }
        }
    }
}
