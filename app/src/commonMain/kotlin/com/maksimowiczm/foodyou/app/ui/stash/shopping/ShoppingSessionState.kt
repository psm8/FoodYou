package com.maksimowiczm.foodyou.app.ui.stash.shopping

import androidx.compose.runtime.Immutable
import com.maksimowiczm.foodyou.common.compose.utility.formatClipZeros
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.search.domain.FoodSearch
import com.maksimowiczm.foodyou.stash.domain.entity.ShoppingSessionItem
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantityUnit
import kotlin.jvm.JvmInline

@Immutable
internal data class ShoppingSessionStashOption(
    val id: StashDefinitionId,
    val name: String,
)

@Immutable
internal data class ShoppingSessionSelectedProduct(
    val id: FoodId.Product,
    val name: String,
    val quantityUnit: StashQuantityUnit,
) {
    companion object {
        fun from(product: FoodSearch.Product): ShoppingSessionSelectedProduct =
            ShoppingSessionSelectedProduct(
                id = product.id,
                name = product.headline,
                quantityUnit = if (product.isLiquid) StashQuantityUnit.Milliliter else StashQuantityUnit.Gram,
            )
    }
}

@JvmInline
internal value class ShoppingSessionListItemId(
    val value: String,
)

@Immutable
internal data class ShoppingSessionListItem(
    val id: ShoppingSessionListItemId,
    val quantityText: String,
    internal val sessionItem: ShoppingSessionItem,
) {
    val name: String
        get() = sessionItem.snapshot.name

    val quantityUnit: StashQuantityUnit
        get() = sessionItem.quantity.unit

    val totalCalories: Double
        get() = (sessionItem.snapshot.nutritionFacts.energy.value ?: 0.0) * (parsedQuantityAmount / 100.0)

    private val parsedQuantityAmount: Double
        get() = quantityText.toDoubleOrNull() ?: sessionItem.quantity.amount

    fun updateQuantity(quantityText: String): ShoppingSessionListItem = copy(quantityText = quantityText)

    fun saveQuantity(): ShoppingSessionListItem? {
        val quantity = quantityUnit.toQuantityOrNull(quantityText) ?: return null
        return copy(quantityText = quantityText, sessionItem = sessionItem.copy(quantity = quantity))
    }

    companion object {
        fun from(
            id: ShoppingSessionListItemId,
            sessionItem: ShoppingSessionItem,
        ): ShoppingSessionListItem =
            ShoppingSessionListItem(
                id = id,
                quantityText = sessionItem.quantity.amount.formatClipZeros(),
                sessionItem = sessionItem,
            )
    }
}

@Immutable
internal data class ShoppingSessionUiState(
    val isLoading: Boolean = true,
    val isConfirming: Boolean = false,
    val stashOptions: List<ShoppingSessionStashOption> = emptyList(),
    val selectedStashId: StashDefinitionId? = null,
    val selectedProduct: ShoppingSessionSelectedProduct? = null,
    val pendingQuantity: String = "",
    val items: List<ShoppingSessionListItem> = emptyList(),
    val error: ShoppingSessionUiError? = null,
) {
    val requiresStashSelection: Boolean
        get() = stashOptions.size > 1

    val selectedStashName: String
        get() = stashOptions.firstOrNull { it.id == selectedStashId }?.name.orEmpty()

    val totalCalories: Double
        get() = items.sumOf(ShoppingSessionListItem::totalCalories)

    val pendingQuantityValue: StashQuantity?
        get() = selectedProduct?.quantityUnit?.toQuantityOrNull(pendingQuantity)

    val canAddPendingProduct: Boolean
        get() = !isLoading && selectedStashId != null && selectedProduct != null && pendingQuantityValue != null

    val canConfirm: Boolean
        get() =
            !isLoading &&
                !isConfirming &&
                selectedStashId != null &&
                items.isNotEmpty() &&
                items.all { it.quantityUnit.toQuantityOrNull(it.quantityText) != null }
}

internal enum class ShoppingSessionUiError {
    StashSelectionRequired,
    ProductSelectionRequired,
    InvalidQuantity,
    StartFailed,
    AddFailed,
    ConfirmFailed,
}

internal sealed interface ShoppingSessionEvent {
    data class Finished(
        val stashId: StashDefinitionId,
    ) : ShoppingSessionEvent
}

internal fun StashQuantityUnit.toQuantityOrNull(value: String): StashQuantity? {
    val amount = value.toDoubleOrNull() ?: return null
    if (amount <= 0.0) {
        return null
    }

    return when (this) {
        StashQuantityUnit.Gram -> StashQuantity.grams(amount)
        StashQuantityUnit.Milliliter -> StashQuantity.milliliters(amount)
        StashQuantityUnit.Fraction -> null
    }
}
