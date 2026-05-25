package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement

internal fun Product.toStashMeasurementOrNull(measurement: Measurement): StashMeasurement? {
    if (!supportsStashMeasurement(measurement)) {
        return null
    }

    val rawValue = measurement.rawValue
    if (!rawValue.isFinite() || rawValue <= 0.0) {
        return null
    }

    return StashMeasurement(measurement)
}

internal fun Product.supportsStashMeasurement(measurement: Measurement): Boolean =
    when (measurement) {
        is Measurement.Gram -> !isLiquid
        is Measurement.Ounce -> !isLiquid
        is Measurement.Milliliter -> isLiquid
        is Measurement.FluidOunce -> isLiquid
        is Measurement.Package -> packageWeight != null && packageWeight > 0.0
        is Measurement.Serving -> servingWeight != null && servingWeight > 0.0
    }