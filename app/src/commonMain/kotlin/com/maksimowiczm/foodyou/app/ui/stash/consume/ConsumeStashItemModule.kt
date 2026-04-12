package com.maksimowiczm.foodyou.app.ui.stash.consume

import com.maksimowiczm.foodyou.stash.domain.entity.StashItemId
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel

fun Module.consumeStashItemModule() {
    viewModel { (itemId: StashItemId) ->
        ConsumeStashItemViewModel(
            itemId = itemId,
            stashRepository = get(),
            mealRepository = get(),
            dateProvider = get(),
            consumeFromStashUseCase = get(),
        )
    }
}
