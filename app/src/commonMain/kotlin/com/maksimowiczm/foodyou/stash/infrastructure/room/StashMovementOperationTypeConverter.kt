package com.maksimowiczm.foodyou.stash.infrastructure.room

import androidx.room.TypeConverter
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation

@Suppress("unused")
internal class StashMovementOperationTypeConverter {
    @TypeConverter
    fun fromStashMovementOperation(operation: StashMovementOperation): Int =
        when (operation) {
            StashMovementOperation.Purchase -> StashMovementOperationSQLConstants.PURCHASE
            StashMovementOperation.CreateSnapshot -> StashMovementOperationSQLConstants.CREATE_SNAPSHOT
            StashMovementOperation.ManualQuickAdd ->
                StashMovementOperationSQLConstants.MANUAL_QUICK_ADD
            StashMovementOperation.DirectConsume -> StashMovementOperationSQLConstants.DIRECT_CONSUME
            StashMovementOperation.IngredientSubtract ->
                StashMovementOperationSQLConstants.INGREDIENT_SUBTRACT
            StashMovementOperation.ReturnToStash -> StashMovementOperationSQLConstants.RETURN_TO_STASH
            StashMovementOperation.ManualAdjust -> StashMovementOperationSQLConstants.MANUAL_ADJUST
            StashMovementOperation.AutoReversalOnEdit ->
                StashMovementOperationSQLConstants.AUTO_REVERSAL_ON_EDIT
            StashMovementOperation.AutoReversalOnDelete ->
                StashMovementOperationSQLConstants.AUTO_REVERSAL_ON_DELETE
        }

    @TypeConverter
    fun toStashMovementOperation(value: Int): StashMovementOperation =
        when (value) {
            StashMovementOperationSQLConstants.PURCHASE -> StashMovementOperation.Purchase
            StashMovementOperationSQLConstants.CREATE_SNAPSHOT ->
                StashMovementOperation.CreateSnapshot
            StashMovementOperationSQLConstants.MANUAL_QUICK_ADD ->
                StashMovementOperation.ManualQuickAdd
            StashMovementOperationSQLConstants.DIRECT_CONSUME ->
                StashMovementOperation.DirectConsume
            StashMovementOperationSQLConstants.INGREDIENT_SUBTRACT ->
                StashMovementOperation.IngredientSubtract
            StashMovementOperationSQLConstants.RETURN_TO_STASH ->
                StashMovementOperation.ReturnToStash
            StashMovementOperationSQLConstants.MANUAL_ADJUST ->
                StashMovementOperation.ManualAdjust
            StashMovementOperationSQLConstants.AUTO_REVERSAL_ON_EDIT ->
                StashMovementOperation.AutoReversalOnEdit
            StashMovementOperationSQLConstants.AUTO_REVERSAL_ON_DELETE ->
                StashMovementOperation.AutoReversalOnDelete
            else -> error("Unknown stash movement operation: $value")
        }
}

internal object StashMovementOperationSQLConstants {
    const val PURCHASE = 0
    const val CREATE_SNAPSHOT = 1
    const val DIRECT_CONSUME = 2
    const val INGREDIENT_SUBTRACT = 3
    const val RETURN_TO_STASH = 4
    const val MANUAL_ADJUST = 5
    const val AUTO_REVERSAL_ON_EDIT = 6
    const val AUTO_REVERSAL_ON_DELETE = 7
    const val MANUAL_QUICK_ADD = 8
}
