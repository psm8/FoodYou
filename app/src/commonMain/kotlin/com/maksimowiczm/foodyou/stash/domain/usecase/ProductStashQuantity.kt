package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantityUnit

internal fun Product.toStashQuantityOrNull(measurement: Measurement): StashQuantity? {
    val weight = weight(measurement) ?: return null
    if (weight <= 0.0) {
        return null
    }

    return when (measurement) {
        is Measurement.Gram,
        is Measurement.Ounce ->
            if (isLiquid) {
                null
            } else {
                StashQuantity.grams(weight)
            }

        is Measurement.Milliliter,
        is Measurement.FluidOunce ->
            if (isLiquid) {
                StashQuantity.milliliters(weight)
            } else {
                null
            }

        is Measurement.Package,
        is Measurement.Serving ->
            if (isLiquid) {
                StashQuantity.milliliters(weight)
            } else {
                StashQuantity.grams(weight)
            }
    }
}

internal fun Product.supportsStashQuantity(quantity: StashQuantity): Boolean =
    when (quantity.unit) {
        StashQuantityUnit.Gram -> !isLiquid
        StashQuantityUnit.Milliliter -> isLiquid
        StashQuantityUnit.Fraction -> false
    }
