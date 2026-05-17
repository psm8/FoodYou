package com.maksimowiczm.foodyou.stash.domain.entity

import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.food.sum
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.common.domain.measurement.type
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import kotlin.jvm.JvmInline

@JvmInline value class ShoppingSessionId(val value: String)

@JvmInline value class ShoppingSessionItemId(val value: String)

data class ShoppingSessionItem(
    val id: ShoppingSessionItemId,
    val productId: FoodId.Product,
    val snapshot: RawProductSnapshot,
    val measurement: StashMeasurement,
) {
    val totalNutritionFacts: NutritionFacts =
        snapshot.nutritionFacts * (measurement.measurement.rawValue / 100.0)
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
        snapshot: RawProductSnapshot,
        measurement: StashMeasurement,
    ): ShoppingSession {
        val existingItem =
            items.firstOrNull {
                it.productId == productId && it.measurement.type == measurement.type
            }
        if (existingItem == null) {
            return copy(
                items =
                    items +
                        ShoppingSessionItem(
                            id = ShoppingSessionItemId("${id.value}-item-${items.size + 1}"),
                            productId = productId,
                            snapshot = snapshot,
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

    fun updateQuantity(itemId: ShoppingSessionItemId, measurement: StashMeasurement): ShoppingSession =
        copy(
            items =
                items.map { item ->
                    if (item.id == itemId) {
                        item.copy(measurement = measurement)
                    } else {
                        item
                    }
                }
        )
}


