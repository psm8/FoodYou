package com.maksimowiczm.foodyou.stash.infrastructure.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class StashItemDao {
    @Query(
        """
        SELECT *
        FROM StashEntry
        WHERE stashId = :stashId
          AND rawValue > 0
        ORDER BY createdAtEpochSeconds DESC, id DESC
        """
    )
    abstract fun observeItems(stashId: Long): Flow<List<StashMeasurementEntity>>

    @Insert abstract suspend fun insertStashItem(entity: StashMeasurementEntity): Long

    @Update abstract suspend fun updateStashItem(entity: StashMeasurementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertStashItem(entity: StashMeasurementEntity)

    @Delete abstract suspend fun deleteStashItem(entity: StashMeasurementEntity)

    @Query(
        """
        SELECT *
        FROM StashEntry
        WHERE id = :id
        """
    )
    abstract suspend fun getStashItem(id: Long): StashMeasurementEntity?
}
