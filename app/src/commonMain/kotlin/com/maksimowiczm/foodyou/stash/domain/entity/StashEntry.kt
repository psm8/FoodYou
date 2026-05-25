package com.maksimowiczm.foodyou.stash.domain.entity

import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import kotlinx.datetime.LocalDateTime

data class StashEntry(
    val id: StashEntryId,
    val stashId: StashDefinitionId,
    val foodRef: StashFoodRef,
    val measurement: StashMeasurement,
    val createdAt: LocalDateTime,
) {
    val baseUnit: MeasurementType
        get() = measurement.type

    fun quantityChangeFrom(previous: StashEntry): StashMeasurement {
        require(stashId == previous.stashId) { "Cannot compare stash entries from different stashes" }
        return measurement - previous.measurement
    }

    fun deletionQuantityChange(): StashMeasurement = measurement.negate()

    companion object {
        fun new(
            stashId: StashDefinitionId,
            foodRef: StashFoodRef,
            measurement: StashMeasurement,
            createdAt: LocalDateTime,
        ): StashEntry =
            StashEntry(
                id = StashEntryId(0),
                stashId = stashId,
                foodRef = foodRef,
                measurement = measurement,
                createdAt = createdAt,
            )
    }
}
