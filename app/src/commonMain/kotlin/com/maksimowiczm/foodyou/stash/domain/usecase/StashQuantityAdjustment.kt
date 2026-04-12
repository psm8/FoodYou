package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity

sealed interface StashQuantityAdjustment {
    val quantity: StashQuantity

    data class SetTo(override val quantity: StashQuantity) : StashQuantityAdjustment

    data class ChangeBy(override val quantity: StashQuantity) : StashQuantityAdjustment
}
