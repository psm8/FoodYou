package com.maksimowiczm.foodyou.app.ui.stash.shopping

import androidx.compose.runtime.Immutable
import com.maksimowiczm.foodyou.common.compose.utility.formatClipZeros
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.common.domain.measurement.from
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.common.domain.measurement.type
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.search.domain.FoodSearch
import com.maksimowiczm.foodyou.stash.domain.entity.ShoppingSessionItem
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantityUnit
import com.maksimowiczm.foodyou.stash.domain.entity.toMeasurement
import kotlin.jvm.JvmInline

@Immutable
internal data class ShoppingSessionStashOption(val id: StashDefinitionId, val name: String)

@Immutable
internal data class ShoppingSessionSelectedProduct(
    val id: FoodId.Product,
    val name: String,
    val isLiquid: Boolean,
    val totalWeight: Double?,
    val servingWeight: Double?,
    val suggestedMeasurement: Measurement,
    val possibleMeasurementTypes: List<MeasurementType>,
    val suggestions: List<Measurement>,
) {
    fun toStashQuantityOrNull(measurement: Measurement?): StashQuantity? {
        val selectedMeasurement = measurement ?: return null
        val weight =
            when (selectedMeasurement) {
                is Measurement.Gram -> selectedMeasurement.value
                is Measurement.Milliliter -> selectedMeasurement.value
                is Measurement.Ounce -> selectedMeasurement.metric
                is Measurement.FluidOunce -> selectedMeasurement.metric
                is Measurement.Package -> totalWeight?.times(selectedMeasurement.quantity)
                is Measurement.Serving -> servingWeight?.times(selectedMeasurement.quantity)
            }
        if (weight == null || weight <= 0.0) {
            return null
        }

        return if (isLiquid) {
            StashQuantity.milliliters(weight)
        } else {
            StashQuantity.grams(weight)
        }
    }

    companion object {
        fun from(product: FoodSearch.Product): ShoppingSessionSelectedProduct {
            val possibleMeasurementTypes =
                MeasurementType.entries.filter { type ->
                    when (type) {
                        MeasurementType.Gram -> !product.isLiquid
                        MeasurementType.Ounce -> !product.isLiquid
                        MeasurementType.Milliliter -> product.isLiquid
                        MeasurementType.FluidOunce -> product.isLiquid
                        MeasurementType.Package -> product.totalWeight != null
                        MeasurementType.Serving -> product.servingWeight != null
                    }
                }
            val defaultSuggestions = possibleMeasurementTypes.map { type ->
                when (type) {
                    MeasurementType.Gram -> Measurement.Gram(Measurement.Gram.DEFAULT)
                    MeasurementType.Ounce -> Measurement.Ounce(Measurement.Ounce.DEFAULT)
                    MeasurementType.Milliliter ->
                        Measurement.Milliliter(Measurement.Milliliter.DEFAULT)
                    MeasurementType.FluidOunce ->
                        Measurement.FluidOunce(Measurement.FluidOunce.DEFAULT)
                    MeasurementType.Package -> Measurement.Package(Measurement.Package.DEFAULT)
                    MeasurementType.Serving -> Measurement.Serving(Measurement.Serving.DEFAULT)
                }
            }

            return ShoppingSessionSelectedProduct(
                id = product.id,
                name = product.headline,
                isLiquid = product.isLiquid,
                totalWeight = product.totalWeight,
                servingWeight = product.servingWeight,
                suggestedMeasurement = product.suggestedMeasurement,
                possibleMeasurementTypes = possibleMeasurementTypes,
                suggestions = listOf(product.suggestedMeasurement) + defaultSuggestions.distinct(),
            )
        }
    }
}

@JvmInline internal value class ShoppingSessionListItemId(val value: String)

@Immutable
internal data class ShoppingSessionListItem(
    val id: ShoppingSessionListItemId,
    val quantityText: String,
    internal val sessionItem: ShoppingSessionItem,
) {
    val name: String
        get() = sessionItem.snapshot.name

    val measurement: Measurement
        get() = sessionItem.measurement

    val quantityUnit: StashQuantityUnit
        get() = sessionItem.quantity.unit

    val totalCalories: Double
        get() =
            (sessionItem.snapshot.nutritionFacts.energy.value ?: 0.0) *
                (parsedQuantityAmount / 100.0)

    private val parsedQuantityAmount: Double
        get() = quantityText.toDoubleOrNull() ?: sessionItem.quantity.amount

    fun updateQuantity(quantityText: String): ShoppingSessionListItem {
        val quantity = quantityUnit.toQuantityOrNull(quantityText)
        if (quantity == null) {
            return copy(quantityText = quantityText)
        }

        val updatedMeasurement =
            sessionItem.measurement.scaleToQuantityOrFallback(
                previousQuantity = sessionItem.quantity,
                updatedQuantity = quantity,
            )

        return copy(
            quantityText = quantityText,
            sessionItem = sessionItem.copy(quantity = quantity, measurement = updatedMeasurement),
        )
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

@Immutable
internal data class ShoppingSessionUiState(
    val isLoading: Boolean = true,
    val isConfirming: Boolean = false,
    val stashOptions: List<ShoppingSessionStashOption> = emptyList(),
    val selectedStashId: StashDefinitionId? = null,
    val selectedProduct: ShoppingSessionSelectedProduct? = null,
    val pendingMeasurement: Measurement? = null,
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
        get() = selectedProduct?.toStashQuantityOrNull(pendingMeasurement)

    val canAddPendingProduct: Boolean
        get() =
            !isLoading &&
                selectedStashId != null &&
                selectedProduct != null &&
                pendingQuantityValue != null

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
    data class Finished(val stashId: StashDefinitionId) : ShoppingSessionEvent
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
