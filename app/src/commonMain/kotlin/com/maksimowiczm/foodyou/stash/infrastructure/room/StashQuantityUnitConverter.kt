package com.maksimowiczm.foodyou.stash.infrastructure.room

import androidx.room.TypeConverter
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantityUnit

@Suppress("unused")
internal class StashQuantityUnitConverter {
    @TypeConverter
    fun fromStashQuantityUnit(unit: StashQuantityUnit): Int =
        when (unit) {
            StashQuantityUnit.Gram -> StashQuantitySQLConstants.GRAM
            StashQuantityUnit.Milliliter -> StashQuantitySQLConstants.MILLILITER
            StashQuantityUnit.Fraction -> StashQuantitySQLConstants.FRACTION
        }

    @TypeConverter
    fun toStashQuantityUnit(value: Int): StashQuantityUnit =
        when (value) {
            StashQuantitySQLConstants.GRAM -> StashQuantityUnit.Gram
            StashQuantitySQLConstants.MILLILITER -> StashQuantityUnit.Milliliter
            StashQuantitySQLConstants.FRACTION -> StashQuantityUnit.Fraction
            else -> error("Unknown stash quantity unit: $value")
        }
}

internal object StashQuantitySQLConstants {
    const val GRAM = 0
    const val MILLILITER = 1
    const val FRACTION = 2
}
