package com.maksimowiczm.foodyou.app.ui.home.personalization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.settings.domain.entity.HomeCard
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import com.maksimowiczm.foodyou.settings.domain.entity.forHomePersonalization
import com.maksimowiczm.foodyou.settings.domain.entity.mergeHomePersonalizationOrder
import com.maksimowiczm.foodyou.stash.domain.repository.StashOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

internal class HomePersonalizationViewModel(
    private val settingsRepository: UserPreferencesRepository<Settings>,
    stashRepository: StashRepository,
    stashOwnerProvider: StashOwnerProvider,
) : ViewModel() {
    private val ownerId = stashOwnerProvider.current()

    private val stateFlow =
        combine(
            settingsRepository.observe(),
            stashRepository.observeStashes(ownerId).map { it.isNotEmpty() },
        ) { settings, hasStash ->
            HomePersonalizationState(
                order = settings.homeCardOrder.forHomePersonalization(hasStash),
                isStashToggleVisible = hasStash,
                isStashCardEnabled = settings.showStashHomeCard,
            )
        }

    val state =
        stateFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(2_000),
            initialValue = runBlocking { stateFlow.first() },
        )

    fun updateOrder(order: List<HomeCard>) {
        viewModelScope.launch {
            settingsRepository.update { copy(homeCardOrder = homeCardOrder.mergeHomePersonalizationOrder(order)) }
        }
    }

    fun updateStashVisibility(isEnabled: Boolean) {
        viewModelScope.launch { settingsRepository.update { copy(showStashHomeCard = isEnabled) } }
    }
}

internal data class HomePersonalizationState(
    val order: List<HomeCard> = HomeCard.defaultOrder.filterNot { it == HomeCard.Stash },
    val isStashToggleVisible: Boolean = false,
    val isStashCardEnabled: Boolean = true,
)
