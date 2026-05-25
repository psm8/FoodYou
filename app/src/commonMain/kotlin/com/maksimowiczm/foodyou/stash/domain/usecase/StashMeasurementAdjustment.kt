package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement

sealed interface StashMeasurementAdjustment {
    val measurement: StashMeasurement

    data class SetTo(override val measurement: StashMeasurement) : StashMeasurementAdjustment

    data class ChangeBy(override val measurement: StashMeasurement) : StashMeasurementAdjustment
}