package com.maksimowiczm.foodyou.stash.infrastructure.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "StashDefinition",
    indices = [Index(value = ["ownerId"]), Index(value = ["ownerId", "name"], unique = true)],
)
data class StashDefinitionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerId: String,
    val name: String,
    val createdAtEpochSeconds: Long,
    val ordering: Int,
)
