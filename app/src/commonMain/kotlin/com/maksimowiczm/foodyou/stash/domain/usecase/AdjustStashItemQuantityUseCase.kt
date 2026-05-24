package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.common.log.logAndReturnFailure
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntry
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.StashFoodRef
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import com.maksimowiczm.foodyou.stash.domain.repository.StashOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import kotlin.math.abs
import kotlinx.coroutines.flow.first

sealed interface AdjustStashItemQuantityError {
    data class ItemNotFound(val itemId: StashEntryId) : AdjustStashItemQuantityError

    data class QuantityUnitMismatch(
        val expected: MeasurementType,
        val actual: MeasurementType,
    ) : AdjustStashItemQuantityError

    data class QuantityBelowZero(val measurement: StashMeasurement) : AdjustStashItemQuantityError
}

class AdjustStashItemQuantityUseCase(
    private val stashRepository: StashRepository,
    private val stashOwnerProvider: StashOwnerProvider,
    private val dateProvider: DateProvider,
    private val transactionProvider: TransactionProvider,
    private val logger: Logger,
) {
    suspend fun adjust(
        itemId: StashEntryId,
        adjustment: StashMeasurementAdjustment,
        action: ManualStashAction,
    ): Result<StashEntry, AdjustStashItemQuantityError> {
        val ownerId = stashOwnerProvider.current()
        return transactionProvider.withTransaction {
            val ownedStashIds = stashRepository.observeStashes(ownerId).first().map { it.id }.toSet()
            val item = stashRepository.getItem(itemId)
            if (item == null || item.stashId !in ownedStashIds) {
                return@withTransaction logger.logAndReturnFailure(
                    tag = TAG,
                    error = AdjustStashItemQuantityError.ItemNotFound(itemId),
                    message = { "Stash item $itemId not found for owner $ownerId." },
                )
            }

            if (item.measurement.type != adjustment.measurement.type) {
                return@withTransaction logger.logAndReturnFailure(
                    tag = TAG,
                    error =
                        AdjustStashItemQuantityError.QuantityUnitMismatch(
                            expected = item.measurement.type,
                            actual = adjustment.measurement.type,
                        ),
                    message = { "Cannot adjust stash item $itemId with mismatched measurement types." },
                )
            }

            val targetMeasurement =
                when (adjustment) {
                    is StashMeasurementAdjustment.ChangeBy -> item.measurement + adjustment.measurement
                    is StashMeasurementAdjustment.SetTo -> adjustment.measurement
                }
            if (targetMeasurement.measurement.rawValue < -EPSILON) {
                return@withTransaction logger.logAndReturnFailure(
                    tag = TAG,
                    error = AdjustStashItemQuantityError.QuantityBelowZero(targetMeasurement),
                    message = { "Cannot adjust stash item $itemId below zero." },
                )
            }

            val normalizedTarget = targetMeasurement.normalize()
            if (normalizedTarget.isSameAmountAs(item.measurement)) {
                return@withTransaction Ok(item)
            }

            val updatedItem = item.copy(measurement = normalizedTarget)
            if (updatedItem.measurement.measurement.rawValue <= EPSILON && updatedItem.canDeleteWhenEmpty()) {
                stashRepository.deleteItem(updatedItem.id)
            } else {
                stashRepository.updateItem(updatedItem)
            }
            stashRepository.insertMovement(
                StashMovement.new(
                    stashId = item.stashId,
                    itemId = item.id,
                    operation = StashMovementOperation.ManualAdjust,
                    measurementChange = normalizedTarget - item.measurement,
                    linkedDiaryEntryId = null,
                    createdAt = dateProvider.now(),
                    note = action.toMovementNote(),
                )
            )
            Ok(updatedItem)
        }
    }

    private fun StashMeasurement.isSameAmountAs(other: StashMeasurement): Boolean =
        type == other.type && abs(measurement.rawValue - other.measurement.rawValue) <= EPSILON

    private fun StashEntry.canDeleteWhenEmpty(): Boolean =
        when (foodRef) {
            is StashFoodRef.Product -> false
            is StashFoodRef.Recipe -> true
        }

    private companion object {
        const val TAG = "AdjustStashItemQuantityUseCase"
        const val EPSILON = 1e-6
    }
}
