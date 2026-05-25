package com.maksimowiczm.foodyou.stash.infrastructure

import com.maksimowiczm.foodyou.stash.domain.entity.StashOwnerId
import com.maksimowiczm.foodyou.stash.domain.repository.StashOwnerProvider

internal class LocalStashOwnerProvider : StashOwnerProvider {
    override fun current(): StashOwnerId = StashOwnerId.Local
}
