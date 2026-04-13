package com.maksimowiczm.foodyou.app.ui.stash

import androidx.compose.runtime.Composable
import com.maksimowiczm.foodyou.common.compose.utility.formatClipZeros
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantityUnit
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

internal fun String.toStashQuantityOrNull(
    unit: StashQuantityUnit,
    requirePositive: Boolean = true,
): StashQuantity? {
    val parsedAmount = parseStashQuantityAmountOrNull(unit) ?: return null
    if (requirePositive && parsedAmount <= 0.0) {
        return null
    }

    return when (unit) {
        StashQuantityUnit.Gram -> StashQuantity.grams(parsedAmount)
        StashQuantityUnit.Milliliter -> StashQuantity.milliliters(parsedAmount)
        StashQuantityUnit.Fraction -> StashQuantity.fraction(parsedAmount)
    }
}

internal fun StashQuantity.toDisplayQuantity(): StashQuantityDisplay =
    when (unit) {
        StashQuantityUnit.Gram -> promotedMetricQuantity(StashQuantityDisplayUnit.Gram, StashQuantityDisplayUnit.Kilogram)
        StashQuantityUnit.Milliliter ->
            promotedMetricQuantity(StashQuantityDisplayUnit.Milliliter, StashQuantityDisplayUnit.Liter)
        StashQuantityUnit.Fraction -> StashQuantityDisplay(amount = amount, unit = StashQuantityDisplayUnit.Fraction)
    }

@Composable
internal fun StashQuantity.displayLabel(): String {
    val displayQuantity = toDisplayQuantity()
    return "${displayQuantity.amount.formatClipZeros()} ${displayQuantity.unit.label()}"
}

private fun String.parseStashQuantityAmountOrNull(unit: StashQuantityUnit): Double? {
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

private fun StashQuantity.promotedMetricQuantity(
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
