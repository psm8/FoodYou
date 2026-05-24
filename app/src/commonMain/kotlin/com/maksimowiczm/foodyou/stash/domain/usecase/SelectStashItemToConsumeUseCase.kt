package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntry
import com.maksimowiczm.foodyou.stash.domain.entity.StashFoodRef
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
    ): Flow<List<StashEntry>> =
        stashRepository.observeStashContents(stashId).map { items ->
            items.filter { item ->
                item.measurement.measurement.rawValue > 0.0 && item.matches(type)
            }
        }

    private fun StashEntry.matches(type: ConsumableStashItemType?): Boolean =
        when (val foodRef = foodRef) {
            is StashFoodRef.Product -> type == null || type == ConsumableStashItemType.RawProduct
            is StashFoodRef.Recipe -> type == null || type == ConsumableStashItemType.AnonymousDish
        }
}
