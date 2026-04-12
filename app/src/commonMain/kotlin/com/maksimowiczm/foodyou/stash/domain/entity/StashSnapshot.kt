package com.maksimowiczm.foodyou.stash.domain.entity

import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
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
    val totalAmount: StashQuantity,
) : StashSnapshot {
    init {
        require(totalWeight >= 0.0) { "Dish total weight must not be negative" }
        require(totalAmount.amount > 0.0) { "Dish total amount must be greater than 0" }
    }

    override val servingWeight: Double = totalWeight / totalAmount.amount

    companion object {
        fun from(recipe: Recipe, totalAmount: StashQuantity): AnonymousDishSnapshot =
            from(recipe = recipe, totalAmount = totalAmount, servingsMade = recipe.servings)

        fun from(
            recipe: Recipe,
            totalAmount: StashQuantity,
            servingsMade: Int,
        ): AnonymousDishSnapshot {
            require(servingsMade > 0) { "Dish servings must be greater than 0" }
            val totalWeight =
                when (totalAmount.unit) {
                    StashQuantityUnit.Fraction -> {
                        val batchMultiplier = servingsMade.toDouble() / recipe.servings.toDouble()
                        recipe.totalWeight * batchMultiplier
                    }

                    StashQuantityUnit.Gram,
                    StashQuantityUnit.Milliliter -> totalAmount.amount
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
