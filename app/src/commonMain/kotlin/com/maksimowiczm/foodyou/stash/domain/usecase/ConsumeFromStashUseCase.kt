package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.common.domain.measurement.from
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.common.domain.measurement.type
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.common.log.logAndReturnFailure
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFood
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodProduct
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId
import com.maksimowiczm.foodyou.fooddiary.domain.repository.FoodDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.domain.repository.MealRepository
import com.maksimowiczm.foodyou.stash.domain.entity.AnonymousDishSnapshot
import com.maksimowiczm.foodyou.stash.domain.entity.LinkedDiaryEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.RawProductSnapshot
import com.maksimowiczm.foodyou.stash.domain.entity.StashItem
import com.maksimowiczm.foodyou.stash.domain.entity.StashItemId
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.LocalDate

sealed interface ConsumeFromStashError {
    data class ItemNotFound(val id: StashItemId) : ConsumeFromStashError

    data object MealNotFound : ConsumeFromStashError

    data object NonPositiveAmount : ConsumeFromStashError

    data object InvalidAmount : ConsumeFromStashError

    data class InsufficientQuantity(
        val available: StashMeasurement,
        val requested: StashMeasurement,
    ) : ConsumeFromStashError

    data object MissingSnapshotWeight : ConsumeFromStashError
}

class ConsumeFromStashUseCase(
    private val stashRepository: StashRepository,
    private val entryRepository: FoodDiaryEntryRepository,
    private val mealRepository: MealRepository,
    private val transactionProvider: TransactionProvider,
    private val dateProvider: DateProvider,
    private val logger: Logger,
) {
    suspend fun consume(
        itemId: StashItemId,
        mealId: Long,
        amountEaten: StashMeasurement,
        dateOverride: LocalDate? = null,
    ): Result<FoodDiaryEntryId, ConsumeFromStashError> {
        if (amountEaten.measurement.rawValue <= 0.0) {
            return logger.logAndReturnFailure(
                tag = TAG,
                error = ConsumeFromStashError.NonPositiveAmount,
                message = { "Consumed amount must be greater than 0." },
            )
        }

        // The diary entry, remaining stash quantity, and audit movement must commit together so
        // linked edit/delete reversals always see a complete stash history.
        return transactionProvider.withTransaction {
            val item = stashRepository.getItem(itemId)
            if (item == null) {
                return@withTransaction logger.logAndReturnFailure(
                    tag = TAG,
                    error = ConsumeFromStashError.ItemNotFound(itemId),
                    message = { "Stash item with id $itemId not found." },
                )
            }

            val meal = mealRepository.observeMeal(mealId).firstOrNull()
            if (meal == null) {
                return@withTransaction logger.logAndReturnFailure(
                    tag = TAG,
                    error = ConsumeFromStashError.MealNotFound,
                    message = { "Meal with id $mealId not found." },
                )
            }

            val plan =
                item.planConsumption(amountEaten) ?: return@withTransaction logger.logAndReturnFailure(
                    tag = TAG,
                    error = ConsumeFromStashError.InvalidAmount,
                    message = { "Amount $amountEaten is not supported for stash item ${item.id}." },
                )

            if (item.measurement.measurement.rawValue + EPSILON < plan.measurementChange.measurement.rawValue) {
                return@withTransaction logger.logAndReturnFailure(
                    tag = TAG,
                    error =
                        ConsumeFromStashError.InsufficientQuantity(
                            available = item.measurement,
                            requested = plan.measurementChange,
                        ),
                    message = {
                        "Cannot consume ${plan.measurementChange.measurement.rawValue} from stash item ${item.id}; only ${item.measurement.measurement.rawValue} remains."
                    },
                )
            }

            val snapshotWeight = item.snapshot.totalWeight
            if (plan.requiresSnapshotWeight && (snapshotWeight == null || snapshotWeight <= EPSILON)) {
                return@withTransaction logger.logAndReturnFailure(
                    tag = TAG,
                    error = ConsumeFromStashError.MissingSnapshotWeight,
                    message = { "Stash item ${item.id} has no weight data for weight-equivalent consumption." },
                )
            }

            val now = dateProvider.now()
            val entryId =
                entryRepository.insert(
                    measurement = plan.measurement,
                    mealId = mealId,
                    date = dateOverride ?: now.date,
                    food = item.snapshot.toDiaryFood(),
                    createdAt = now,
                )

            val remainingQuantity = (item.measurement - plan.measurementChange).normalize()
            if (remainingQuantity.measurement.rawValue <= EPSILON && item.canDeleteWhenEmpty()) {
                stashRepository.deleteItem(item.id)
            } else {
                stashRepository.updateItem(item.copy(measurement = remainingQuantity))
            }

            stashRepository.insertMovement(
                StashMovement.new(
                    stashId = item.stashId,
                    itemId = item.id,
                    operation = StashMovementOperation.DirectConsume,
                    measurementChange = plan.measurementChange.negate(),
                    linkedDiaryEntryId = LinkedDiaryEntryId(entryId.value),
                    createdAt = now,
                )
            )

            Ok(entryId)
        }
    }

    private fun StashItem.planConsumption(requestedAmount: StashMeasurement): ConsumptionPlan? =
        when (val snapshot = snapshot) {
            is RawProductSnapshot -> rawProductPlan(requestedAmount)
            is AnonymousDishSnapshot -> snapshot.anonymousDishPlan(measurement, requestedAmount)
        }

    private fun StashItem.rawProductPlan(requestedAmount: StashMeasurement): ConsumptionPlan? {
        // Same type required — raw products only support same-type consumption.
        if (requestedAmount.type != measurement.type) {
            return null
        }

        return ConsumptionPlan(measurementChange = requestedAmount, measurement = requestedAmount.measurement)
    }

    private fun AnonymousDishSnapshot.anonymousDishPlan(
        availableMeasurement: StashMeasurement,
        requestedAmount: StashMeasurement,
    ): ConsumptionPlan? {
        // Same-type: requested type matches available type — direct deduction.
        if (requestedAmount.type == availableMeasurement.type) {
            return ConsumptionPlan(measurementChange = requestedAmount, measurement = requestedAmount.measurement)
        }

        // Cross-type consumption: dishes stored in servings/packages can be consumed in weight units.
        // Weight is converted back to servings proportionally:
        //   servingsConsumed = requestedWeight / totalWeight * totalAmount.rawValue
        val weightType = when (requestedAmount.type) {
            MeasurementType.Gram -> true
            MeasurementType.Milliliter -> true
            MeasurementType.Ounce -> true
            MeasurementType.FluidOunce -> true
            else -> false
        }

        val canCrossConvert = when (availableMeasurement.type) {
            MeasurementType.Serving -> true
            MeasurementType.Package -> true
            else -> false
        }

        if (!weightType || !canCrossConvert) {
            return null // Unsupported cross-type combination.
        }

        // Liquid/solid constraint: liquid dishes only accept liquid weight, solid only solid weight.
        val expectedWeightType =
            if (isLiquid) {
                requestedAmount.type == MeasurementType.Milliliter || requestedAmount.type == MeasurementType.FluidOunce
            } else {
                requestedAmount.type == MeasurementType.Gram || requestedAmount.type == MeasurementType.Ounce
            }
        if (!expectedWeightType) {
            return null
        }

        // Convert weight to servings proportionally.
        val requestedWeight = requestedAmount.measurement.rawValue
        val rawValueChange = totalAmount.rawValue * (requestedWeight / totalWeight)
        val measurementChange = StashMeasurement(Measurement.from(availableMeasurement.type, rawValueChange))

        return ConsumptionPlan(
            measurementChange = measurementChange,
            measurement = requestedAmount.measurement,
            requiresSnapshotWeight = true,
        )
    }

    private fun com.maksimowiczm.foodyou.stash.domain.entity.StashSnapshot.toDiaryFood(): DiaryFood =
        when (this) {
            is RawProductSnapshot ->
                DiaryFoodProduct(
                    name = displayName(),
                    nutritionFacts = nutritionFacts,
                    servingWeight = servingWeight,
                    totalWeight = totalWeight,
                    isLiquid = isLiquid,
                    source = source,
                    note = note,
                )

            is AnonymousDishSnapshot ->
                DiaryFoodProduct(
                    name = name,
                    nutritionFacts = nutritionFacts,
                    servingWeight =
                        servingWeight.takeIf {
                            totalAmount.type == MeasurementType.Serving
                        },
                    totalWeight = totalWeight,
                    isLiquid = isLiquid,
                    source = FoodSource(type = FoodSource.Type.User),
                    note = note,
                )
        }

    private fun RawProductSnapshot.displayName(): String =
        buildString {
            append(name)
            if (!brand.isNullOrBlank()) {
                append(" ($brand)")
            }
        }

    private fun StashItem.canDeleteWhenEmpty(): Boolean =
        when (val snapshot = snapshot) {
            is RawProductSnapshot -> snapshot.productId == null
            is AnonymousDishSnapshot -> true
        }

    private data class ConsumptionPlan(
        val measurementChange: StashMeasurement,
        val measurement: Measurement,
        val requiresSnapshotWeight: Boolean = false,
    )

    private companion object {
        const val TAG = "ConsumeFromStashUseCase"
        const val EPSILON = 0.000001
    }
}