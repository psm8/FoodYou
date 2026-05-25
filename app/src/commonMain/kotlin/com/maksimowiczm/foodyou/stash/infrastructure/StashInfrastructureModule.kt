package com.maksimowiczm.foodyou.stash.infrastructure

import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import com.maksimowiczm.foodyou.stash.domain.repository.StashOwnerProvider
import com.maksimowiczm.foodyou.stash.infrastructure.repository.RoomStashRepository
import com.maksimowiczm.foodyou.stash.infrastructure.room.StashDatabase
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.scope.Scope
import org.koin.dsl.bind

internal fun Module.stashInfrastructureModule() {
    factory { database.stashDefinitionDao }
    factory { database.stashItemDao }
    factory { database.stashMovementDao }
    factoryOf(::LocalStashOwnerProvider).bind<StashOwnerProvider>()
    factoryOf(::RoomStashRepository).bind<StashRepository>()
}

private val Scope.database: StashDatabase
    get() = get()
