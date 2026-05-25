package com.maksimowiczm.foodyou.stash.infrastructure.room

interface StashDatabase {
    val stashDefinitionDao: StashDefinitionDao
    val stashItemDao: StashItemDao
    val stashMovementDao: StashMovementDao
}
