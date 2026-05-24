package com.maksimowiczm.foodyou.app.ui.home.stash

import androidx.compose.runtime.Immutable
import com.maksimowiczm.foodyou.app.ui.stash.StashFoodDisplay
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import com.maksimowiczm.foodyou.stash.domain.usecase.HomeStashSummary

@Immutable
internal data class HomeStashCardUiState(
    val isLoading: Boolean = false,
    val isVisible: Boolean = false,
    val totalItemCount: Int = 0,
    val stashNames: List<String> = emptyList(),
    val preferredStashId: StashDefinitionId? = null,
    val recentItems: List<HomeStashCardItemUi> = emptyList(),
) {
    companion object {
        fun from(
            summary: HomeStashSummary?,
            recentItems: List<HomeStashCardItemUi> = emptyList(),
            isLoading: Boolean = false,
        ): HomeStashCardUiState {
            if (summary == null) {
                return HomeStashCardUiState(isLoading = isLoading)
            }

            return HomeStashCardUiState(
                isLoading = isLoading,
                isVisible = true,
                totalItemCount = summary.totalItemCount,
                stashNames = summary.stashes.map { it.name },
                preferredStashId = summary.recentItems.firstOrNull()?.stashId ?: summary.stashes.firstOrNull()?.id,
                recentItems = recentItems,
            )
        }
    }
}

@Immutable
internal data class HomeStashCardItemUi(
    val id: StashEntryId,
    val name: String,
    val isLoading: Boolean,
    val stashName: String,
    val quantity: StashMeasurement,
) {
    companion object {
        fun from(
            item: com.maksimowiczm.foodyou.stash.domain.usecase.HomeStashSummaryItem,
            display: StashFoodDisplay,
        ) =
            HomeStashCardItemUi(
                id = item.id,
                name = display.name,
                isLoading = display.isLoading,
                stashName = item.stashName,
                quantity = item.measurement,
            )
    }
}
