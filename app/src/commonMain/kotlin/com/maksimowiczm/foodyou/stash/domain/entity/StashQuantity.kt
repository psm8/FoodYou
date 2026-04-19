package com.maksimowiczm.foodyou.stash.domain.entity

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement

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
