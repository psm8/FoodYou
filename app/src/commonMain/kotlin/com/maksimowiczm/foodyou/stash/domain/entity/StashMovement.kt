package com.maksimowiczm.foodyou.stash.domain.entity

import kotlinx.datetime.LocalDateTime

enum class StashMovementOperation {
    Purchase,
    CreateSnapshot,
    ManualQuickAdd,
    DirectConsume,
    IngredientSubtract,
    ReturnToStash,
    ManualAdjust,
    AutoReversalOnEdit,
    AutoReversalOnDelete,
}

data class StashMovement(
    val id: StashMovementId,
    val stashId: StashDefinitionId,
    val itemId: StashItemId,
    val operation: StashMovementOperation,
    val quantityChange: StashQuantity,
    val linkedDiaryEntryId: LinkedDiaryEntryId?,
    val note: String? = null,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun new(
            stashId: StashDefinitionId,
            itemId: StashItemId,
            operation: StashMovementOperation,
            quantityChange: StashQuantity,
            linkedDiaryEntryId: LinkedDiaryEntryId?,
            createdAt: LocalDateTime,
            note: String? = null,
        ): StashMovement =
            StashMovement(
                id = StashMovementId(0),
                stashId = stashId,
                itemId = itemId,
                operation = operation,
                quantityChange = quantityChange,
                linkedDiaryEntryId = linkedDiaryEntryId,
                note = note,
                createdAt = createdAt,
            )
    }
}
