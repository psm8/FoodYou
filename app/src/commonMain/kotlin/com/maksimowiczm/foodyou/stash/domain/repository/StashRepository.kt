package com.maksimowiczm.foodyou.stash.domain.repository

import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinition
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.LinkedDiaryEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntry
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementId
import com.maksimowiczm.foodyou.stash.domain.entity.StashOwnerId
import kotlinx.coroutines.flow.Flow

interface StashRepository {
    fun observeStashes(ownerId: StashOwnerId): Flow<List<StashDefinition>>

    fun observeStashContents(stashId: StashDefinitionId): Flow<List<StashEntry>>

    fun observeMovementHistory(stashId: StashDefinitionId): Flow<List<StashMovement>>

    suspend fun getItem(id: StashEntryId): StashEntry?

    suspend fun getLinkedDiaryEntryMovements(linkedDiaryEntryId: LinkedDiaryEntryId): List<StashMovement>

    suspend fun insertStash(stash: StashDefinition): StashDefinitionId

    suspend fun updateStash(stash: StashDefinition)

    suspend fun deleteStash(id: StashDefinitionId)

    suspend fun insertItem(item: StashEntry): StashEntryId

    suspend fun updateItem(item: StashEntry)

    suspend fun deleteItem(id: StashEntryId)

    suspend fun insertMovement(movement: StashMovement): StashMovementId
}
