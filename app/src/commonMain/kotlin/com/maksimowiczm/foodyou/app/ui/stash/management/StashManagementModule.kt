package com.maksimowiczm.foodyou.app.ui.stash.management

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf

fun Module.stashManagementModule() {
    viewModelOf(::StashManagementViewModel)
}
