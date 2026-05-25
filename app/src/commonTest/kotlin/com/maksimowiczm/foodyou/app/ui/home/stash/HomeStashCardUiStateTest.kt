package com.maksimowiczm.foodyou.app.ui.home.stash

import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import com.maksimowiczm.foodyou.stash.domain.entity.StashFoodRef
import com.maksimowiczm.foodyou.stash.domain.usecase.HomeStashSummary
import com.maksimowiczm.foodyou.stash.domain.usecase.HomeStashSummaryItem
import com.maksimowiczm.foodyou.stash.domain.usecase.HomeStashSummaryStash
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntryId
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDateTime

class HomeStashCardUiStateTest {
    @Test
    fun `when multiple stashes exist, preferred stash follows the most recent item`() {
        val uiState =
            HomeStashCardUiState.from(
                summary =
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
                                    id = StashEntryId(10L),
                                    foodRef =
                                        StashFoodRef.Recipe(
                                            FoodId.Recipe(5L),
                                            totalWeight = 300.0,
                                            totalAmount = com.maksimowiczm.foodyou.common.domain.measurement.Measurement.Gram(300.0),
                                        ),
                                    stashId = StashDefinitionId(2L),
                                    stashName = "Pantry",
                                    measurement = StashMeasurement.grams(300.0),
                                    createdAt = LocalDateTime(2025, 1, 3, 10, 0),
                                ),
                                HomeStashSummaryItem(
                                    id = StashEntryId(9L),
                                    foodRef = StashFoodRef.Product(FoodId.Product(4L)),
                                    stashId = StashDefinitionId(1L),
                                    stashName = "Fridge",
                                    measurement = StashMeasurement.grams(150.0),
                                    createdAt = LocalDateTime(2025, 1, 2, 10, 0),
                                ),
                            ),
                    ),
                recentItems =
                    listOf(
                        HomeStashCardItemUi(
                            id = StashEntryId(10L),
                            name = "Soup",
                            isLoading = false,
                            stashName = "Pantry",
                            quantity = StashMeasurement.grams(300.0),
                        ),
                        HomeStashCardItemUi(
                            id = StashEntryId(9L),
                            name = "Yogurt",
                            isLoading = false,
                            stashName = "Fridge",
                            quantity = StashMeasurement.grams(150.0),
                        ),
                    ),
            )

        assertEquals(StashDefinitionId(2L), uiState.preferredStashId)
    }
}
