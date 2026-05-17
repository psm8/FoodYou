package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinition
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashItem
import com.maksimowiczm.foodyou.stash.domain.entity.StashItemId
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import com.maksimowiczm.foodyou.stash.domain.repository.StashOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDateTime

data class HomeStashSummary(
    val stashes: List<HomeStashSummaryStash>,
    val totalItemCount: Int,
    val recentItems: List<HomeStashSummaryItem>,
)

data class HomeStashSummaryStash(
    val id: StashDefinitionId,
    val name: String,
    val itemCount: Int,
)

data class HomeStashSummaryItem(
    val id: StashItemId,
    val name: String,
    val stashId: StashDefinitionId,
    val stashName: String,
    val measurement: StashMeasurement,
    val createdAt: LocalDateTime,
)

class ObserveHomeStashSummaryUseCase(
    private val stashRepository: StashRepository,
    private val stashOwnerProvider: StashOwnerProvider,
) {
    fun observe(recentItemLimit: Int = DEFAULT_RECENT_ITEM_LIMIT): Flow<HomeStashSummary?> {
        val ownerId = stashOwnerProvider.current()

        return stashRepository.observeStashes(ownerId).flatMapLatest { stashes ->
            val orderedStashes =
                stashes.sortedWith(compareBy<StashDefinition>({ it.ordering }, { it.id.value }))
            if (orderedStashes.isEmpty()) {
                return@flatMapLatest flowOf(null)
            }

            combine(orderedStashes.map { stashRepository.observeStashContents(it.id) }) { itemsByStash: Array<List<StashItem>> ->
                buildHomeStashSummary(
                    stashes = orderedStashes,
                    itemsByStash = itemsByStash.toList(),
                    recentItemLimit = recentItemLimit,
                )
            }
        }
    }

    private companion object {
        const val DEFAULT_RECENT_ITEM_LIMIT = 5
    }
}

internal fun buildHomeStashSummary(
    stashes: List<StashDefinition>,
    itemsByStash: List<List<StashItem>>,
    recentItemLimit: Int = 5,
): HomeStashSummary? {
    val normalizedRecentItemLimit = recentItemLimit.coerceIn(3, 5)
    val availableItemsByStash =
        itemsByStash.map { items -> items.filter { it.measurement.measurement.rawValue > 0.0 } }
    val stashSummaries =
        stashes.mapIndexed { index, stash ->
            HomeStashSummaryStash(
                id = stash.id,
                name = stash.name.value,
                itemCount = availableItemsByStash.getOrElse(index) { emptyList() }.size,
            )
        }

    val recentItems =
        stashes
            .mapIndexed { index, stash ->
                availableItemsByStash.getOrElse(index) { emptyList() }.map { item ->
                    HomeStashSummaryItem(
                        id = item.id,
                        name = item.snapshot.name,
                        stashId = stash.id,
                        stashName = stash.name.value,
                        measurement = item.measurement,
                        createdAt = item.createdAt,
                    )
                }
            }.flatten()
            .sortedWith(
                compareByDescending<HomeStashSummaryItem> { it.createdAt }.thenByDescending { it.id.value }
            )
            .take(normalizedRecentItemLimit)

    val totalItemCount = stashSummaries.sumOf(HomeStashSummaryStash::itemCount)
    if (totalItemCount == 0) {
        return null
    }

    return HomeStashSummary(
        stashes = stashSummaries.filter { it.itemCount > 0 },
        totalItemCount = totalItemCount,
        recentItems = recentItems,
    )
}

