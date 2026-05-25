package com.maksimowiczm.foodyou.stash.infrastructure.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
abstract class StashMovementDao {
    @Query(
        """
        SELECT *
        FROM StashMovement
        WHERE stashId = :stashId
        ORDER BY createdAtEpochSeconds DESC, id DESC
        """
    )
    abstract fun observeMovements(stashId: Long): Flow<List<StashMovementEntity>>

    @Query(
        """
        SELECT *
        FROM StashMovement
        WHERE linkedDiaryEntryId = :linkedDiaryEntryId
        ORDER BY createdAtEpochSeconds ASC, id ASC
        """
    )
    abstract suspend fun getLinkedDiaryEntryMovements(linkedDiaryEntryId: Long): List<StashMovementEntity>

    @Insert abstract suspend fun insertStashMovement(entity: StashMovementEntity): Long
}
