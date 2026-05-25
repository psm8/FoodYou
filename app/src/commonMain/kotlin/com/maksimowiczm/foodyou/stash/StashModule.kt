package com.maksimowiczm.foodyou.stash

import com.maksimowiczm.foodyou.stash.domain.stashDomainModule
import com.maksimowiczm.foodyou.stash.infrastructure.stashInfrastructureModule
import org.koin.dsl.module

val stashModule = module {
    stashDomainModule()
    stashInfrastructureModule()
}
