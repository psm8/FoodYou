package com.maksimowiczm.foodyou.app.ui.stash.consume

import com.maksimowiczm.foodyou.stash.domain.entity.StashEntryId
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel

fun Module.consumeStashItemModule() {
    viewModel { (itemId: StashEntryId) ->
        ConsumeStashItemViewModel(
            itemId = itemId,
            stashRepository = get(),
            mealRepository = get(),
            dateProvider = get(),
            observeFoodUseCase = get(),
            consumeFromStashUseCase = get(),
        )
    }
}
