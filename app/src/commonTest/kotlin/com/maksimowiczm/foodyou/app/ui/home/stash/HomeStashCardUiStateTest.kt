package com.maksimowiczm.foodyou.app.ui.home.stash

import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
import com.maksimowiczm.foodyou.stash.domain.usecase.HomeStashSummary
import com.maksimowiczm.foodyou.stash.domain.usecase.HomeStashSummaryItem
import com.maksimowiczm.foodyou.stash.domain.usecase.HomeStashSummaryStash
import com.maksimowiczm.foodyou.stash.domain.entity.StashItemId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDateTime

class HomeStashCardUiStateTest {
    @Test
    fun `when multiple stashes exist, preferred stash follows the most recent item`() {
        val uiState =
            HomeStashCardUiState.from(
                HomeStashSummary(
                    stashes =
                        listOf(
                            HomeStashSummaryStash(
                                id = StashDefinitionId(1L),
                                name = "Fridge",
                                itemCount = 2,
                            ),
                            HomeStashSummaryStash(
                                id = StashDefinitionId(2L),
                                name = "Pantry",
                                itemCount = 1,
                            ),
                        ),
                    totalItemCount = 3,
                    recentItems =
                        listOf(
                            HomeStashSummaryItem(
                                id = StashItemId(10L),
                                name = "Soup",
                                stashId = StashDefinitionId(2L),
                                stashName = "Pantry",
                                quantity = StashQuantity.grams(300.0),
                                createdAt = LocalDateTime(2025, 1, 3, 10, 0),
                            ),
                            HomeStashSummaryItem(
                                id = StashItemId(9L),
                                name = "Yogurt",
                                stashId = StashDefinitionId(1L),
                                stashName = "Fridge",
                                quantity = StashQuantity.grams(150.0),
                                createdAt = LocalDateTime(2025, 1, 2, 10, 0),
                            ),
                        ),
                )
            )

        assertEquals(StashDefinitionId(2L), uiState.preferredStashId)
    }
}
