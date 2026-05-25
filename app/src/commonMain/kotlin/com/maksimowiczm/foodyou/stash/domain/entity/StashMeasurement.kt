package com.maksimowiczm.foodyou.stash.domain.entity

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.common.domain.measurement.from
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.common.domain.measurement.type

data class StashMeasurement(val measurement: Measurement) {
    init {
        require(measurement.rawValue.isFinite()) { "StashMeasurement value must be finite" }
    }

    val type: MeasurementType get() = measurement.type

    operator fun plus(other: StashMeasurement): StashMeasurement {
        requireSameType(other)
        return StashMeasurement(Measurement.from(type, measurement.rawValue + other.measurement.rawValue))
    }

    operator fun minus(other: StashMeasurement): StashMeasurement {
        requireSameType(other)
        return StashMeasurement(Measurement.from(type, measurement.rawValue - other.measurement.rawValue))
    }

    fun negate(): StashMeasurement =
        StashMeasurement(Measurement.from(type, -measurement.rawValue))

    fun normalize(): StashMeasurement {
        val rawValue = measurement.rawValue
        return if (rawValue > -1e-9 && rawValue < 1e-9) {
            StashMeasurement(Measurement.from(type, 0.0))
        } else {
            this
        }
    }

    private fun requireSameType(other: StashMeasurement) {
        require(type == other.type) {
            "Cannot combine StashMeasurements with different types: $type vs ${other.type}"
        }
    }

    companion object {
        fun grams(amount: Double): StashMeasurement = StashMeasurement(Measurement.Gram(amount))
        fun milliliters(amount: Double): StashMeasurement = StashMeasurement(Measurement.Milliliter(amount))
        fun servings(amount: Double): StashMeasurement = StashMeasurement(Measurement.Serving(amount))
        fun packages(amount: Double): StashMeasurement = StashMeasurement(Measurement.Package(amount))
        fun ounces(amount: Double): StashMeasurement = StashMeasurement(Measurement.Ounce(amount))
        fun fluidOunces(amount: Double): StashMeasurement = StashMeasurement(Measurement.FluidOunce(amount))
    }
}