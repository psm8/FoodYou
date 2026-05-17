package com.maksimowiczm.foodyou.stash.domain.entity

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.from
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.common.domain.measurement.type

enum class StashQuantityUnit {
    Gram,
    Milliliter,
    Fraction,
}

data class StashQuantity(val amount: Double, val unit: StashQuantityUnit) {
    init {
        require(amount.isFinite()) { "Stash quantity must be finite" }
    }

    operator fun plus(other: StashQuantity): StashQuantity {
        requireSameUnit(other)
        return copy(amount = amount + other.amount)
    }

    operator fun minus(other: StashQuantity): StashQuantity {
        requireSameUnit(other)
        return copy(amount = amount - other.amount)
    }

    fun negate(): StashQuantity = copy(amount = -amount)

    private fun requireSameUnit(other: StashQuantity) {
        require(unit == other.unit) {
            "Cannot combine stash quantities with different units: $unit vs ${other.unit}"
        }
    }

    companion object {
        fun grams(amount: Double): StashQuantity = StashQuantity(amount, StashQuantityUnit.Gram)

        fun milliliters(amount: Double): StashQuantity =
            StashQuantity(amount, StashQuantityUnit.Milliliter)

        fun fraction(amount: Double): StashQuantity =
            StashQuantity(amount, StashQuantityUnit.Fraction)
    }
}

fun StashQuantity.toMeasurement(): Measurement =
    when (unit) {
        StashQuantityUnit.Gram -> Measurement.Gram(amount)
        StashQuantityUnit.Milliliter -> Measurement.Milliliter(amount)
        StashQuantityUnit.Fraction -> Measurement.Serving(amount)
    }

fun Measurement.scaleToQuantityOrFallback(
    previousQuantity: StashQuantity,
    updatedQuantity: StashQuantity,
): Measurement {
    if (previousQuantity.amount <= 0.0) {
        return updatedQuantity.toMeasurement()
    }

    val ratio = updatedQuantity.amount / previousQuantity.amount
    return Measurement.from(type, rawValue * ratio)
}
