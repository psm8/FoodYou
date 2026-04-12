package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.stash.domain.entity.AnonymousDishSnapshot
import com.maksimowiczm.foodyou.stash.domain.entity.RawProductSnapshot
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashItem
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ConsumableStashItemType {
    RawProduct,
    AnonymousDish,
}

class SelectStashItemToConsumeUseCase(
    private val stashRepository: StashRepository,
) {
    fun observe(
        stashId: StashDefinitionId,
        type: ConsumableStashItemType? = null,
    ): Flow<List<StashItem>> =
        stashRepository.observeStashContents(stashId).map { items ->
            items.filter { item ->
                item.quantity.amount > 0.0 &&
                    when (type) {
                        null -> true
                        ConsumableStashItemType.RawProduct -> item.snapshot is RawProductSnapshot
                        ConsumableStashItemType.AnonymousDish ->
                            item.snapshot is AnonymousDishSnapshot
                    }
            }
        }
}
