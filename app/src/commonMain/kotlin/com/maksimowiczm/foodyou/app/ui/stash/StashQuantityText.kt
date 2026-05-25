package com.maksimowiczm.foodyou.app.ui.stash

import androidx.compose.runtime.Composable
import com.maksimowiczm.foodyou.common.compose.utility.formatClipZeros
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.common.domain.measurement.from
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import foodyou.app.generated.resources.Res
import foodyou.app.generated.resources.unit_gram_short
import foodyou.app.generated.resources.unit_kilogram_short
import foodyou.app.generated.resources.unit_liter_short
import foodyou.app.generated.resources.unit_milliliter_short
import foodyou.app.generated.resources.unit_stash_fraction_short
import kotlin.math.abs
import org.jetbrains.compose.resources.stringResource

internal data class StashQuantityDisplay(
    val amount: Double,
    val unit: StashQuantityDisplayUnit,
)

internal enum class StashQuantityDisplayUnit {
    Gram,
    Kilogram,
    Milliliter,
    Liter,
    Fraction,
}

internal fun String.toStashMeasurementOrNull(
    type: MeasurementType,
    requirePositive: Boolean = true,
): StashMeasurement? {
    val parsedAmount = parseStashMeasurementAmountOrNull(type) ?: return null
    if (requirePositive && parsedAmount <= 0.0) {
        return null
    }

    return when (type) {
        MeasurementType.Gram -> StashMeasurement.grams(parsedAmount)
        MeasurementType.Milliliter -> StashMeasurement.milliliters(parsedAmount)
        MeasurementType.Package -> StashMeasurement.packages(parsedAmount)
        MeasurementType.Serving -> StashMeasurement.servings(parsedAmount)
        else -> null
    }
}

// Keep the old name for backwards compatibility
internal fun String.toStashQuantityOrNull(
    unit: MeasurementType,
    requirePositive: Boolean = true,
): StashMeasurement? = toStashMeasurementOrNull(unit, requirePositive)

internal fun StashMeasurement.toDisplayQuantity(): StashQuantityDisplay {
    val type = this.type
    val amount = measurement.rawValue
    return when (type) {
        MeasurementType.Gram -> promotedMetricQuantity(amount, StashQuantityDisplayUnit.Gram, StashQuantityDisplayUnit.Kilogram)
        MeasurementType.Milliliter ->
            promotedMetricQuantity(amount, StashQuantityDisplayUnit.Milliliter, StashQuantityDisplayUnit.Liter)
        MeasurementType.Package,
        MeasurementType.Serving -> StashQuantityDisplay(amount = amount, unit = StashQuantityDisplayUnit.Fraction)
        else -> StashQuantityDisplay(amount = amount, unit = StashQuantityDisplayUnit.Fraction)
    }
}

@Composable
internal fun StashMeasurement.displayLabel(): String {
    val displayQuantity = toDisplayQuantity()
    return "${displayQuantity.amount.formatClipZeros()} ${displayQuantity.unit.label()}"
}

private fun String.parseStashMeasurementAmountOrNull(type: MeasurementType): Double? {
    val normalizedText = trim().lowercase().replace(',', '.')
    if (normalizedText.isBlank()) {
        return null
    }

    namedFractions[normalizedText]?.let { return it }

    val fractionMatch = fractionRegex.matchEntire(normalizedText) ?: return normalizedText.toDoubleOrNull()
    val numerator = fractionMatch.groupValues[1].toDoubleOrNull() ?: return null
    val denominator = fractionMatch.groupValues[2].toDoubleOrNull() ?: return null
    if (denominator == 0.0) {
        return null
    }

    return numerator / denominator
}

private fun promotedMetricQuantity(
    amount: Double,
    baseUnit: StashQuantityDisplayUnit,
    promotedUnit: StashQuantityDisplayUnit,
): StashQuantityDisplay =
    if (abs(amount) >= PROMOTED_UNIT_THRESHOLD) {
        StashQuantityDisplay(amount = amount / PROMOTED_UNIT_THRESHOLD, unit = promotedUnit)
    } else {
        StashQuantityDisplay(amount = amount, unit = baseUnit)
    }

@Composable
private fun StashQuantityDisplayUnit.label(): String =
    when (this) {
        StashQuantityDisplayUnit.Gram -> stringResource(Res.string.unit_gram_short)
        StashQuantityDisplayUnit.Kilogram -> stringResource(Res.string.unit_kilogram_short)
        StashQuantityDisplayUnit.Milliliter -> stringResource(Res.string.unit_milliliter_short)
        StashQuantityDisplayUnit.Liter -> stringResource(Res.string.unit_liter_short)
        StashQuantityDisplayUnit.Fraction -> stringResource(Res.string.unit_stash_fraction_short)
    }

private val namedFractions =
    mapOf(
        "half" to 0.5,
        "third" to (1.0 / 3.0),
        "quarter" to 0.25,
    )

private val fractionRegex = Regex("^([+-]?\\d+(?:\\.\\d+)?)\\s*/\\s*([+-]?\\d+(?:\\.\\d+)?)$")

private const val PROMOTED_UNIT_THRESHOLD = 1000.0

