package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantityUnit

internal fun Product.supportsStashQuantity(quantity: StashQuantity): Boolean =
    when (quantity.unit) {
        StashQuantityUnit.Gram -> !isLiquid
        StashQuantityUnit.Milliliter -> isLiquid
        StashQuantityUnit.Fraction -> false
    }
