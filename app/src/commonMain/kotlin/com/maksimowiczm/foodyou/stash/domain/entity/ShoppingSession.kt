package com.maksimowiczm.foodyou.stash.domain.entity

import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.food.sum
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.from
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
    val measurement: Measurement,
    val quantity: StashQuantity,
) {
    val totalNutritionFacts: NutritionFacts = snapshot.nutritionFacts * (quantity.amount / 100.0)
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
        measurement: Measurement,
        quantity: StashQuantity,
    ): ShoppingSession {
        val existingItem = items.firstOrNull { it.productId == productId }
        if (existingItem == null) {
            return copy(
                items =
                    items +
                        ShoppingSessionItem(
                            id = ShoppingSessionItemId("${id.value}-item-${items.size + 1}"),
                            productId = productId,
                            snapshot = snapshot,
                            measurement = measurement,
                            quantity = quantity,
                        )
            )
        }

        return copy(
            items =
                items.map { item ->
                    if (item.id == existingItem.id) {
                        item.copy(
                            measurement =
                                item.measurement.mergeWith(measurement, item.quantity + quantity),
                            quantity = item.quantity + quantity,
                        )
                    } else {
                        item
                    }
                }
        )
    }

    fun updateQuantity(itemId: ShoppingSessionItemId, quantity: StashQuantity): ShoppingSession =
        copy(
            items =
                items.map { item ->
                    if (item.id == itemId) {
                        item.copy(
                            quantity = quantity,
                            measurement =
                                item.measurement.scaleToQuantityOrFallback(
                                    previousQuantity = item.quantity,
                                    updatedQuantity = quantity,
                                ),
                        )
                    } else {
                        item
                    }
                }
        )
}

private fun Measurement.mergeWith(other: Measurement, mergedQuantity: StashQuantity): Measurement {
    if (type == other.type) {
        return Measurement.from(type, rawValue + other.rawValue)
    }

    return mergedQuantity.toMeasurement()
}

private fun Measurement.scaleToQuantityOrFallback(
    previousQuantity: StashQuantity,
    updatedQuantity: StashQuantity,
): Measurement {
    if (previousQuantity.amount <= 0.0) {
        return updatedQuantity.toMeasurement()
    }

    val ratio = updatedQuantity.amount / previousQuantity.amount
    return Measurement.from(type, rawValue * ratio)
}
