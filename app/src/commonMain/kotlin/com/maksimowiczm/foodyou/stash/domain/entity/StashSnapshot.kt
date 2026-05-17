package com.maksimowiczm.foodyou.stash.domain.entity

import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.common.domain.measurement.type
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.entity.Recipe

sealed interface StashSnapshot {
    val name: String
    val nutritionFacts: NutritionFacts
    val note: String?
    val isLiquid: Boolean
    val totalWeight: Double?
    val servingWeight: Double?
}

data class RawProductSnapshot(
    val productId: FoodId.Product?,
    override val name: String,
    val brand: String?,
    val barcode: String?,
    override val note: String?,
    override val isLiquid: Boolean,
    val packageWeight: Double?,
    override val servingWeight: Double?,
    val source: FoodSource,
    override val nutritionFacts: NutritionFacts,
) : StashSnapshot {
    override val totalWeight: Double? = packageWeight

    companion object {
        fun from(product: Product): RawProductSnapshot =
            RawProductSnapshot(
                productId = product.id,
                name = product.name,
                brand = product.brand,
                barcode = product.barcode,
                note = product.note,
                isLiquid = product.isLiquid,
                packageWeight = product.packageWeight,
                servingWeight = product.servingWeight,
                source = product.source,
                nutritionFacts = product.nutritionFacts,
            )
    }
}

data class AnonymousDishSnapshot(
    override val name: String,
    override val nutritionFacts: NutritionFacts,
    override val note: String?,
    override val isLiquid: Boolean,
    override val totalWeight: Double,
    val totalAmount: Measurement,
) : StashSnapshot {
    init {
        require(totalWeight >= 0.0) { "Dish total weight must not be negative" }
        require(totalAmount.rawValue > 0.0) { "Dish total amount must be greater than 0" }
    }

    override val servingWeight: Double = totalWeight / totalAmount.rawValue

    companion object {
        fun from(recipe: Recipe, totalAmount: Measurement): AnonymousDishSnapshot =
            from(recipe = recipe, totalAmount = totalAmount, servingsMade = recipe.servings)

        fun from(
            recipe: Recipe,
            totalAmount: Measurement,
            servingsMade: Int,
        ): AnonymousDishSnapshot {
            require(servingsMade > 0) { "Dish servings must be greater than 0" }
            val totalWeight =
                when (totalAmount.type) {
                    MeasurementType.Serving,
                    MeasurementType.Package -> {
                        val batchMultiplier = servingsMade.toDouble() / recipe.servings.toDouble()
                        recipe.totalWeight * batchMultiplier
                    }

                    MeasurementType.Gram,
                    MeasurementType.Milliliter -> totalAmount.rawValue

                    MeasurementType.Ounce,
                    MeasurementType.FluidOunce -> totalAmount.rawValue
                }
            return AnonymousDishSnapshot(
                name = recipe.name,
                nutritionFacts = recipe.nutritionFacts,
                note = recipe.note,
                isLiquid = recipe.isLiquid,
                totalWeight = totalWeight,
                totalAmount = totalAmount,
            )
        }
    }
}
