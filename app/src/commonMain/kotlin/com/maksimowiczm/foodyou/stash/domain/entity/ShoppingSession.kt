package com.maksimowiczm.foodyou.stash.domain.entity

import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.food.sum
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Product as CatalogProduct
import kotlin.jvm.JvmInline

@JvmInline value class ShoppingSessionId(val value: String)

@JvmInline value class ShoppingSessionItemId(val value: String)

data class ShoppingSessionProductDetails(
    val name: String,
    val isLiquid: Boolean,
    val totalWeight: Double?,
    val servingWeight: Double?,
    val nutritionFacts: NutritionFacts,
) {
    fun metricAmount(measurement: Measurement): Double =
        when (measurement) {
            is Measurement.ImmutableMeasurement -> measurement.metric
            is Measurement.Package -> measurement.weight(requireNotNull(totalWeight))
            is Measurement.Serving -> measurement.weight(requireNotNull(servingWeight))
        }

    companion object {
        fun from(product: CatalogProduct): ShoppingSessionProductDetails =
            ShoppingSessionProductDetails(
                name = product.headline,
                isLiquid = product.isLiquid,
                totalWeight = product.totalWeight,
                servingWeight = product.servingWeight,
                nutritionFacts = product.nutritionFacts,
            )
    }
}

data class ShoppingSessionItem(
    val id: ShoppingSessionItemId,
    val foodRef: StashFoodRef.Product,
    val productDetails: ShoppingSessionProductDetails,
    val measurement: StashMeasurement,
) {
    val productId: FoodId.Product
        get() = foodRef.productId

    val totalNutritionFacts: NutritionFacts =
        productDetails.nutritionFacts * (productDetails.metricAmount(measurement.measurement) / 100.0)
}

data class ShoppingSession(
    val id: ShoppingSessionId,
    val stashId: StashDefinitionId,
    val items: List<ShoppingSessionItem>,
) {
    val totalNutritionFacts: NutritionFacts =
        items.map(ShoppingSessionItem::totalNutritionFacts).sum()

    fun add(
        productId: FoodId.Product,
        foodRef: StashFoodRef.Product,
        productDetails: ShoppingSessionProductDetails,
        measurement: StashMeasurement,
    ): ShoppingSession {
        val existingItem =
            items.firstOrNull {
                it.foodRef.productId == productId && it.measurement.type == measurement.type
            }
        if (existingItem == null) {
            return copy(
                items =
                    items +
                        ShoppingSessionItem(
                            id = ShoppingSessionItemId("${id.value}-item-${items.size + 1}"),
                            foodRef = foodRef,
                            productDetails = productDetails,
                            measurement = measurement,
                        )
            )
        }

        return copy(
            items =
                items.map { item ->
                    if (item.id == existingItem.id) {
                        item.copy(measurement = item.measurement + measurement)
                    } else {
                        item
                    }
                }
        )
    }
}
