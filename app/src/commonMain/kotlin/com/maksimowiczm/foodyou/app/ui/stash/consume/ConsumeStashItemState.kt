package com.maksimowiczm.foodyou.app.ui.stash.consume

import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantityUnit
import kotlinx.datetime.LocalDate

internal data class ConsumeStashItemMeal(
    val id: Long,
    val name: String,
)

internal enum class ConsumeStashItemError {
    ItemNotFound,
    MealNotFound,
    InvalidAmount,
    InsufficientQuantity,
    Unknown,
}

internal data class ConsumeStashItemState(
    val itemName: String = "",
    val remainingQuantity: StashQuantity? = null,
    val amount: String = "",
    val meals: List<ConsumeStashItemMeal> = emptyList(),
    val selectedMealId: Long? = null,
    val today: LocalDate = LocalDate(1970, 1, 1),
    val selectedDate: LocalDate = today,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: ConsumeStashItemError? = null,
) {
    val parsedAmount: StashQuantity?
        get() = remainingQuantity?.unit?.toQuantityOrNull(amount)

    val canSave: Boolean
        get() {
            val remainingQuantity = remainingQuantity ?: return false
            val parsedAmount = parsedAmount ?: return false
            return !isLoading &&
                !isSaving &&
                selectedMealId != null &&
                parsedAmount.amount > 0.0 &&
                parsedAmount.amount <= remainingQuantity.amount
        }
}

internal fun StashQuantityUnit.toQuantityOrNull(amountText: String): StashQuantity? {
    val amount = amountText.toDoubleOrNull() ?: return null
    return when (this) {
        StashQuantityUnit.Gram -> StashQuantity.grams(amount)
        StashQuantityUnit.Milliliter -> StashQuantity.milliliters(amount)
        StashQuantityUnit.Fraction -> StashQuantity.fraction(amount)
    }
}
