package com.maksimowiczm.foodyou.settings.domain.entity

enum class HomeCard {
    Calendar,
    Goals,
    Meals,
    Stash;

    companion object {
        val defaultOrder: List<HomeCard> = entries.toList()
    }
}

internal fun normalizeHomeCardOrder(order: List<HomeCard>): List<HomeCard> {
    val distinctCards = order.distinct()
    return distinctCards + HomeCard.defaultOrder.filterNot(distinctCards::contains)
}

internal fun List<HomeCard>.forHomePersonalization(hasStash: Boolean): List<HomeCard> =
    normalizeHomeCardOrder(this).let { order ->
        if (hasStash) {
            order
        } else {
            order.filterNot(HomeCard::isStash)
        }
    }

internal fun List<HomeCard>.mergeHomePersonalizationOrder(visibleOrder: List<HomeCard>): List<HomeCard> {
    val currentOrder = normalizeHomeCardOrder(this)
    val reorderedVisibleCards = visibleOrder.distinct()
    val visibleCards = reorderedVisibleCards.toSet()
    var visibleIndex = 0

    val mergedOrder =
        buildList {
            currentOrder.forEach { card ->
                if (card in visibleCards) {
                    if (visibleIndex < reorderedVisibleCards.size) {
                        add(reorderedVisibleCards[visibleIndex++])
                    }
                } else {
                    add(card)
                }
            }

            while (visibleIndex < reorderedVisibleCards.size) {
                add(reorderedVisibleCards[visibleIndex++])
            }
        }

    return normalizeHomeCardOrder(mergedOrder)
}

private fun HomeCard.isStash(): Boolean = this == HomeCard.Stash
