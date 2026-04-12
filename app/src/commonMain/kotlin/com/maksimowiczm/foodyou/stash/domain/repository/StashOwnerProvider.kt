package com.maksimowiczm.foodyou.stash.domain.repository

import com.maksimowiczm.foodyou.stash.domain.entity.StashOwnerId

fun interface StashOwnerProvider {
    fun current(): StashOwnerId
}
