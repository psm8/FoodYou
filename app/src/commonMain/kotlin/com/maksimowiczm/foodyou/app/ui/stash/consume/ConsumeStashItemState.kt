package com.maksimowiczm.foodyou.app.ui.stash.consume

import com.maksimowiczm.foodyou.app.ui.stash.toStashMeasurementOrNull
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
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
        val available: StashMeasurement,
        val requested: StashMeasurement,
    ) : ConsumeStashItemError

    data object Unknown : ConsumeStashItemError
}

internal data class ConsumeStashItemState(
    val itemName: String = "",
    val remainingQuantity: StashMeasurement? = null,
    val amount: String = "",
    val meals: List<ConsumeStashItemMeal> = emptyList(),
    val selectedMealId: Long? = null,
    val today: LocalDate = LocalDate(1970, 1, 1),
    val selectedDate: LocalDate = today,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: ConsumeStashItemError? = null,
) {
    val parsedAmount: StashMeasurement?
        get() = remainingQuantity?.type?.let { amount.toStashMeasurementOrNull(it) }

    val canSave: Boolean
        get() {
            remainingQuantity ?: return false
            val parsedAmount = parsedAmount ?: return false
            return !isLoading &&
                !isSaving &&
                selectedMealId != null &&
                parsedAmount.measurement.rawValue > 0.0
        }
}

