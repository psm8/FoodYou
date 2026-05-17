package com.maksimowiczm.foodyou.stash.domain.entity

import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import kotlinx.datetime.LocalDateTime

data class StashItem(
    val id: StashItemId,
    val stashId: StashDefinitionId,
    val snapshot: StashSnapshot,
    val measurement: StashMeasurement,
    val createdAt: LocalDateTime,
) {
    val baseUnit: MeasurementType
        get() = measurement.type

    fun quantityChangeFrom(previous: StashItem): StashMeasurement {
        require(stashId == previous.stashId) { "Cannot compare stash items from different stashes" }
        return measurement - previous.measurement
    }

    fun deletionQuantityChange(): StashMeasurement = measurement.negate()

    companion object {
        fun new(
            stashId: StashDefinitionId,
            snapshot: StashSnapshot,
            measurement: StashMeasurement,
            createdAt: LocalDateTime,
        ): StashItem =
            StashItem(
                id = StashItemId(0),
                stashId = stashId,
                snapshot = snapshot,
                measurement = measurement,
                createdAt = createdAt,
            )
    }
}
