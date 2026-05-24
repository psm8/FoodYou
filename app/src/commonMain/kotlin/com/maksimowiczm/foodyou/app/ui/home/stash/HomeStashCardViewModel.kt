package com.maksimowiczm.foodyou.app.ui.home.stash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.app.ui.stash.StashFoodDisplay
import com.maksimowiczm.foodyou.app.ui.stash.observeStashFoodDisplay
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveFoodUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.ObserveHomeStashSummaryUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

internal class HomeStashCardViewModel(
    observeHomeStashSummaryUseCase: ObserveHomeStashSummaryUseCase,
    observeFoodUseCase: ObserveFoodUseCase,
) : ViewModel() {
    private val summaryLoaded = MutableStateFlow(false)
    private val summary =
        observeHomeStashSummaryUseCase
            .observe()
            .onEach { summaryLoaded.value = true }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = null,
            )

    private val recentItemDisplays =
        summary
            .flatMapLatest { summary ->
                val recentItems = summary?.recentItems.orEmpty()
                if (recentItems.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(recentItems.map { observeFoodUseCase.observeStashFoodDisplay(it.foodRef) }) { displays ->
                        displays.toList()
                    }
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = emptyList(),
            )

    val state =
        combine(summary, recentItemDisplays, summaryLoaded) { summary, recentItemDisplays, summaryLoaded ->
            val resolvedRecentItems =
                summary
                    ?.recentItems
                    ?.mapIndexed { index, item ->
                        HomeStashCardItemUi.from(
                            item = item,
                            display = recentItemDisplays.getOrElse(index) { StashFoodDisplay.loading() },
                        )
                    }.orEmpty()
            HomeStashCardUiState.from(
                summary = summary,
                recentItems = resolvedRecentItems,
                isLoading = !summaryLoaded || resolvedRecentItems.any(HomeStashCardItemUi::isLoading),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(2_000),
            initialValue = HomeStashCardUiState(isLoading = true),
        )
}
