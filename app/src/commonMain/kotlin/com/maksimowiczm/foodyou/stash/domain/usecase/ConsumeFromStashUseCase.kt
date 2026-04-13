package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
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
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantity
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantityUnit
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.LocalDate

sealed interface ConsumeFromStashError {
    data class ItemNotFound(val id: StashItemId) : ConsumeFromStashError

    data object MealNotFound : ConsumeFromStashError

    data object NonPositiveAmount : ConsumeFromStashError

    data object InvalidAmount : ConsumeFromStashError

    data class InsufficientQuantity(
        val available: StashQuantity,
        val requested: StashQuantity,
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
        amountEaten: StashQuantity,
        dateOverride: LocalDate? = null,
    ): Result<FoodDiaryEntryId, ConsumeFromStashError> {
        if (amountEaten.amount <= 0.0) {
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

            if (item.quantity.amount + EPSILON < plan.quantityChange.amount) {
                return@withTransaction logger.logAndReturnFailure(
                    tag = TAG,
                    error =
                        ConsumeFromStashError.InsufficientQuantity(
                            available = item.quantity,
                            requested = plan.quantityChange,
                        ),
                    message = {
                        "Cannot consume ${plan.quantityChange.amount} from stash item ${item.id}; only ${item.quantity.amount} remains."
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

            val remainingQuantity = (item.quantity - plan.quantityChange).normalize()
            if (remainingQuantity.amount <= EPSILON && item.canDeleteWhenEmpty()) {
                stashRepository.deleteItem(item.id)
            } else {
                stashRepository.updateItem(item.copy(quantity = remainingQuantity))
            }

            stashRepository.insertMovement(
                StashMovement.new(
                    stashId = item.stashId,
                    itemId = item.id,
                    operation = StashMovementOperation.DirectConsume,
                    quantityChange = plan.quantityChange.negate(),
                    linkedDiaryEntryId = LinkedDiaryEntryId(entryId.value),
                    createdAt = now,
                )
            )

            Ok(entryId)
        }
    }

    private fun StashItem.planConsumption(requestedAmount: StashQuantity): ConsumptionPlan? =
        when (val snapshot = snapshot) {
            is RawProductSnapshot -> rawProductPlan(requestedAmount)
            is AnonymousDishSnapshot -> snapshot.anonymousDishPlan(quantity, requestedAmount)
        }

    private fun StashItem.rawProductPlan(requestedAmount: StashQuantity): ConsumptionPlan? {
        if (requestedAmount.unit != quantity.unit) {
            return null
        }

        val measurement = requestedAmount.toMeasurement() ?: return null
        return ConsumptionPlan(quantityChange = requestedAmount, measurement = measurement)
    }

    private fun AnonymousDishSnapshot.anonymousDishPlan(
        availableQuantity: StashQuantity,
        requestedAmount: StashQuantity,
    ): ConsumptionPlan? {
        if (requestedAmount.unit == availableQuantity.unit) {
            val measurement = requestedAmount.toMeasurement() ?: return null
            return ConsumptionPlan(
                quantityChange = requestedAmount,
                measurement = measurement,
            )
        }

        if (availableQuantity.unit != StashQuantityUnit.Fraction) {
            return null
        }

        val expectedWeightUnit =
            if (isLiquid) {
                StashQuantityUnit.Milliliter
            } else {
                StashQuantityUnit.Gram
            }
        if (requestedAmount.unit != expectedWeightUnit) {
            return null
        }

        return ConsumptionPlan(
            // Dish snapshots may be stored as fractions of a batch, so weight-based consumption is
            // converted back into the persisted fraction for later reversal/rebalance logic.
            quantityChange =
                StashQuantity.fraction(
                    totalAmount.amount * (requestedAmount.amount / totalWeight)
                ),
            measurement = requestedAmount.toMeasurement() ?: return null,
            requiresSnapshotWeight = true,
        )
    }

    private fun StashQuantity.toMeasurement(): Measurement? =
        when (unit) {
            StashQuantityUnit.Gram -> Measurement.Gram(amount)
            StashQuantityUnit.Milliliter -> Measurement.Milliliter(amount)
            StashQuantityUnit.Fraction -> Measurement.Serving(amount)
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
                            totalAmount.unit == StashQuantityUnit.Fraction
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
        val quantityChange: StashQuantity,
        val measurement: Measurement,
        val requiresSnapshotWeight: Boolean = false,
    )

    private fun StashQuantity.normalize(): StashQuantity =
        if (amount <= EPSILON) {
            copy(amount = 0.0)
        } else {
            this
        }

    private companion object {
        const val TAG = "ConsumeFromStashUseCase"
        const val EPSILON = 0.000001
    }
}
