package com.maksimowiczm.foodyou.stash.domain.entity

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import kotlinx.datetime.LocalDateTime

data class StashItem(
    val id: StashItemId,
    val stashId: StashDefinitionId,
    val snapshot: StashSnapshot,
    val measurement: Measurement? = null,
    val quantity: StashQuantity,
    val createdAt: LocalDateTime,
) {
    val baseUnit: StashQuantityUnit
        get() = quantity.unit

    fun quantityChangeFrom(previous: StashItem): StashQuantity {
        require(stashId == previous.stashId) { "Cannot compare stash items from different stashes" }
        return quantity - previous.quantity
    }

    fun deletionQuantityChange(): StashQuantity = quantity.negate()

    companion object {
        fun new(
            stashId: StashDefinitionId,
            snapshot: StashSnapshot,
            measurement: Measurement? = null,
            quantity: StashQuantity,
            createdAt: LocalDateTime,
        ): StashItem =
            StashItem(
                id = StashItemId(0),
                stashId = stashId,
                snapshot = snapshot,
                measurement = measurement,
                quantity = quantity,
                createdAt = createdAt,
            )
    }
}
