package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinition
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashItem
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovement
import com.maksimowiczm.foodyou.stash.domain.entity.StashName
import com.maksimowiczm.foodyou.stash.domain.entity.StashOwnerId
import com.maksimowiczm.foodyou.stash.domain.repository.StashOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDateTime

data class StashManagementOverview(
    val id: StashDefinitionId,
    val name: StashName,
    val ordering: Int,
    val itemCount: Int,
    val lastModifiedAt: LocalDateTime,
) {
    companion object {
        fun from(
            stash: StashDefinition,
            items: List<StashItem>,
            movements: List<StashMovement>,
        ): StashManagementOverview {
            val lastModifiedAt =
                listOfNotNull(
                    stash.createdAt,
                    items.maxOfOrNull(StashItem::createdAt),
                    movements.maxOfOrNull(StashMovement::createdAt),
                ).maxOrNull() ?: stash.createdAt

            return StashManagementOverview(
                id = stash.id,
                name = stash.name,
                ordering = stash.ordering,
                itemCount = items.size,
                lastModifiedAt = lastModifiedAt,
            )
        }
    }
}

class LoadStashManagementStashesUseCase(
    private val stashRepository: StashRepository,
    private val stashOwnerProvider: StashOwnerProvider,
) {
    suspend fun load(): List<StashManagementOverview> {
        val ownerId = stashOwnerProvider.current()
        val stashes = stashRepository.observeStashes(ownerId).first()

        return stashes.map { stash ->
            val items = stashRepository.observeStashContents(stash.id).first()
            val movements = stashRepository.observeMovementHistory(stash.id).first()
            StashManagementOverview.from(stash, items, movements)
        }
    }
}
