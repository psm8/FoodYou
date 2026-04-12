package com.maksimowiczm.foodyou.settings.domain.entity

import kotlin.test.Test
import kotlin.test.assertEquals

class HomeCardOrderTest {
    @Test
    fun `when normalizing older home card order, it appends stash at the end`() {
        val normalized = normalizeHomeCardOrder(listOf(HomeCard.Calendar, HomeCard.Goals, HomeCard.Meals))

        assertEquals(
            listOf(HomeCard.Calendar, HomeCard.Goals, HomeCard.Meals, HomeCard.Stash),
            normalized,
        )
    }

    @Test
    fun `when personalization hides stash, it keeps only visible cards`() {
        val personalizationOrder =
            listOf(HomeCard.Calendar, HomeCard.Stash, HomeCard.Goals, HomeCard.Meals)
                .forHomePersonalization(hasStash = false)

        assertEquals(
            listOf(HomeCard.Calendar, HomeCard.Goals, HomeCard.Meals),
            personalizationOrder,
        )
    }

    @Test
    fun `when reordering visible cards, hidden stash keeps its previous slot`() {
        val mergedOrder =
            listOf(HomeCard.Calendar, HomeCard.Stash, HomeCard.Goals, HomeCard.Meals)
                .mergeHomePersonalizationOrder(
                    listOf(HomeCard.Meals, HomeCard.Calendar, HomeCard.Goals)
                )

        assertEquals(
            listOf(HomeCard.Meals, HomeCard.Stash, HomeCard.Calendar, HomeCard.Goals),
            mergedOrder,
        )
    }
}
