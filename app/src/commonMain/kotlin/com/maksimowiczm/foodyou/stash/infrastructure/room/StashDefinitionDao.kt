package com.maksimowiczm.foodyou.stash.infrastructure.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class StashDefinitionDao {
    @Query(
        """
        SELECT *
        FROM StashDefinition
        WHERE ownerId = :ownerId
        ORDER BY ordering ASC, createdAtEpochSeconds ASC, id ASC
        """
    )
    abstract fun observeStashes(ownerId: String): Flow<List<StashDefinitionEntity>>

    @Insert abstract suspend fun insertStashDefinition(entity: StashDefinitionEntity): Long

    @Update abstract suspend fun updateStashDefinition(entity: StashDefinitionEntity)

    @Delete abstract suspend fun deleteStashDefinition(entity: StashDefinitionEntity)

    @Query(
        """
        SELECT *
        FROM StashDefinition
        WHERE id = :id
        """
    )
    abstract suspend fun getStashDefinition(id: Long): StashDefinitionEntity?
}
