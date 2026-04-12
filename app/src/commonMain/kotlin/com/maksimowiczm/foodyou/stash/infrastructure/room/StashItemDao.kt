package com.maksimowiczm.foodyou.stash.infrastructure.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class StashItemDao {
    @Query(
        """
        SELECT *
        FROM StashItem
        WHERE stashId = :stashId
          AND quantity > 0
        ORDER BY createdAtEpochSeconds DESC, id DESC
        """
    )
    abstract fun observeItems(stashId: Long): Flow<List<StashItemEntity>>

    @Insert abstract suspend fun insertStashItem(entity: StashItemEntity): Long

    @Update abstract suspend fun updateStashItem(entity: StashItemEntity)

    @Delete abstract suspend fun deleteStashItem(entity: StashItemEntity)

    @Query(
        """
        SELECT *
        FROM StashItem
        WHERE id = :id
        """
    )
    abstract suspend fun getStashItem(id: Long): StashItemEntity?
}
