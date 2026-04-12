package com.maksimowiczm.foodyou.stash.domain.entity

import kotlinx.datetime.LocalDateTime

data class StashDefinition(
    val id: StashDefinitionId,
    val ownerId: StashOwnerId,
    val name: StashName,
    val createdAt: LocalDateTime,
    val ordering: Int,
) {
    init {
        require(ordering >= 0) { "Stash ordering must not be negative" }
    }

    companion object {
        fun new(
            ownerId: StashOwnerId,
            name: StashName,
            createdAt: LocalDateTime,
            ordering: Int,
        ): StashDefinition =
            StashDefinition(
                id = StashDefinitionId(0),
                ownerId = ownerId,
                name = name,
                createdAt = createdAt,
                ordering = ordering,
            )
    }
}
