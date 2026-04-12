package com.maksimowiczm.foodyou.app.ui.stash.browser

import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind

fun Module.stashBrowserModule() {
    factoryOf(::DomainStashBrowserActions).bind<StashBrowserActions>()
    viewModel { (stashId: StashDefinitionId) ->
        StashBrowserViewModel(
            stashId = stashId,
            stashRepository = get(),
            stashOwnerProvider = get(),
            browserActions = get(),
        )
    }
}
