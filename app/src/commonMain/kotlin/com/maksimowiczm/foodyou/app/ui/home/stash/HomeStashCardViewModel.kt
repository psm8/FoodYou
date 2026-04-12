package com.maksimowiczm.foodyou.app.ui.home.stash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.stash.domain.usecase.ObserveHomeStashSummaryUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

internal class HomeStashCardViewModel(
    observeHomeStashSummaryUseCase: ObserveHomeStashSummaryUseCase,
) : ViewModel() {
    private val cardStateFlow = observeHomeStashSummaryUseCase.observe().map(HomeStashCardUiState::from)

    val state =
        cardStateFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(2_000),
            initialValue = runBlocking { cardStateFlow.first() },
        )
}
