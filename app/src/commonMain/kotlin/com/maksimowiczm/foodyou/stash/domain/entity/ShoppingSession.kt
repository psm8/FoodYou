package com.maksimowiczm.foodyou.stash.domain.entity

import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.food.sum
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import kotlin.jvm.JvmInline

@JvmInline value class ShoppingSessionId(val value: String)

@JvmInline value class ShoppingSessionItemId(val value: String)

data class ShoppingSessionItem(
    val id: ShoppingSessionItemId,
    val productId: FoodId.Product,
    val snapshot: RawProductSnapshot,
    val quantity: StashQuantity,
) {
    val totalNutritionFacts: NutritionFacts = snapshot.nutritionFacts * (quantity.amount / 100.0)
}

data class ShoppingSession(
    val id: ShoppingSessionId,
    val stashId: StashDefinitionId,
    val items: List<ShoppingSessionItem>,
) {
    val totalNutritionFacts: NutritionFacts = items.map(ShoppingSessionItem::totalNutritionFacts).sum()

    fun add(
        productId: FoodId.Product,
        snapshot: RawProductSnapshot,
        quantity: StashQuantity,
    ): ShoppingSession =
        copy(
            items =
                items +
                    ShoppingSessionItem(
                        id = ShoppingSessionItemId("${id.value}-item-${items.size + 1}"),
                        productId = productId,
                        snapshot = snapshot,
                        quantity = quantity,
                    )
        )

    fun updateQuantity(
        itemId: ShoppingSessionItemId,
        quantity: StashQuantity,
    ): ShoppingSession =
        copy(
            items =
                items.map { item ->
                    if (item.id == itemId) {
                        item.copy(quantity = quantity)
                    } else {
                        item
                    }
                }
        )
}
