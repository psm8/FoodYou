package com.maksimowiczm.foodyou.app.ui.stash.browser

import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntry
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntryId
import com.maksimowiczm.foodyou.stash.domain.usecase.AdjustStashItemQuantityError
import com.maksimowiczm.foodyou.stash.domain.usecase.AdjustStashItemQuantityUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.MoveStashItemError
import com.maksimowiczm.foodyou.stash.domain.usecase.MoveStashItemUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.RemoveStashItemError
import com.maksimowiczm.foodyou.stash.domain.usecase.RemoveStashItemUseCase
import com.maksimowiczm.foodyou.stash.domain.usecase.StashMeasurementAdjustment

internal interface StashBrowserActions {
    suspend fun remove(
        itemId: StashEntryId,
    ): Result<StashEntry, RemoveStashItemError>

    suspend fun adjust(
        itemId: StashEntryId,
        adjustment: StashMeasurementAdjustment,
    ): Result<StashEntry, AdjustStashItemQuantityError>

    suspend fun move(
        itemId: StashEntryId,
        targetStashId: StashDefinitionId,
    ): Result<StashEntry, MoveStashItemError>
}

internal class DomainStashBrowserActions(
    private val adjustStashItemQuantityUseCase: AdjustStashItemQuantityUseCase,
    private val removeStashItemUseCase: RemoveStashItemUseCase,
    private val moveStashItemUseCase: MoveStashItemUseCase,
) : StashBrowserActions {
    override suspend fun remove(
        itemId: StashEntryId,
    ): Result<StashEntry, RemoveStashItemError> =
        removeStashItemUseCase.remove(itemId = itemId)

    override suspend fun adjust(
        itemId: StashEntryId,
        adjustment: StashMeasurementAdjustment,
    ): Result<StashEntry, AdjustStashItemQuantityError> =
        adjustStashItemQuantityUseCase.adjust(itemId = itemId, adjustment = adjustment)

    override suspend fun move(
        itemId: StashEntryId,
        targetStashId: StashDefinitionId,
    ): Result<StashEntry, MoveStashItemError> =
        moveStashItemUseCase.move(itemId = itemId, targetStashId = targetStashId)
}