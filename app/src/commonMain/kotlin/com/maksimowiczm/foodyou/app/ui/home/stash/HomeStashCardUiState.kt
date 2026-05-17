package com.maksimowiczm.foodyou.app.ui.home.stash

import androidx.compose.runtime.Immutable
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashItemId
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import com.maksimowiczm.foodyou.stash.domain.usecase.HomeStashSummary

@Immutable
internal data class HomeStashCardUiState(
    val isVisible: Boolean = false,
    val totalItemCount: Int = 0,
    val stashNames: List<String> = emptyList(),
    val preferredStashId: StashDefinitionId? = null,
    val recentItems: List<HomeStashCardItemUi> = emptyList(),
) {
    companion object {
        fun from(summary: HomeStashSummary?): HomeStashCardUiState {
            if (summary == null) {
                return HomeStashCardUiState()
            }

            return HomeStashCardUiState(
                isVisible = true,
                totalItemCount = summary.totalItemCount,
                stashNames = summary.stashes.map { it.name },
                preferredStashId = summary.recentItems.firstOrNull()?.stashId ?: summary.stashes.firstOrNull()?.id,
                recentItems = summary.recentItems.map(HomeStashCardItemUi::from),
            )
        }
    }
}

@Immutable
internal data class HomeStashCardItemUi(
    val id: StashItemId,
    val name: String,
    val stashName: String,
    val quantity: StashMeasurement,
) {
    companion object {
        fun from(item: com.maksimowiczm.foodyou.stash.domain.usecase.HomeStashSummaryItem) =
            HomeStashCardItemUi(
                id = item.id,
                name = item.name,
                stashName = item.stashName,
                quantity = item.measurement,
            )
    }
}
