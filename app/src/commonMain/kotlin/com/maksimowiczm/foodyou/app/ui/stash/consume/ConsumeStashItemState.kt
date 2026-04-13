package com.maksimowiczm.foodyou.app.ui.stash.consume

import com.maksimowiczm.foodyou.app.ui.stash.toStashQuantityOrNull
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
import kotlinx.datetime.LocalDate

internal data class ConsumeStashItemMeal(
    val id: Long,
    val name: String,
)

internal sealed interface ConsumeStashItemError {
    data object ItemNotFound : ConsumeStashItemError

    data object MealNotFound : ConsumeStashItemError

    data object InvalidAmount : ConsumeStashItemError

    data class InsufficientQuantity(
        val available: StashQuantity,
        val requested: StashQuantity,
    ) : ConsumeStashItemError

    data object Unknown : ConsumeStashItemError
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
        get() = remainingQuantity?.unit?.let { amount.toStashQuantityOrNull(it) }

    val canSave: Boolean
        get() {
            remainingQuantity ?: return false
            val parsedAmount = parsedAmount ?: return false
            return !isLoading &&
                !isSaving &&
                selectedMealId != null &&
                parsedAmount.amount > 0.0
        }
}
