package com.maksimowiczm.foodyou.app.ui.stash.shopping

import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel

fun Module.shoppingSessionModule() {
    viewModel { (stashId: StashDefinitionId?) ->
        ShoppingSessionViewModel(
            stashId = stashId,
            startShoppingSessionUseCase = get(),
            addToShoppingSessionUseCase = get(),
            confirmShoppingSessionUseCase = get(),
            stashRepository = get(),
            stashOwnerProvider = get(),
        )
    }
}
