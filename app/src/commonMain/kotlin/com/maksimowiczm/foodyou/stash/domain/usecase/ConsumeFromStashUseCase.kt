package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.common.domain.measurement.from
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.common.log.logAndReturnFailure
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.food.domain.repository.RecipeRepository
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId
import com.maksimowiczm.foodyou.fooddiary.domain.repository.FoodDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.domain.repository.MealRepository
import com.maksimowiczm.foodyou.stash.domain.entity.LinkedDiaryEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntry
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.StashFoodRef
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.LocalDate

sealed interface ConsumeFromStashError {
    data class ItemNotFound(val id: StashEntryId) : ConsumeFromStashError

    data object MealNotFound : ConsumeFromStashError

    data object NonPositiveAmount : ConsumeFromStashError

    data object InvalidAmount : ConsumeFromStashError

    data class InsufficientQuantity(
        val available: StashMeasurement,
        val requested: StashMeasurement,
    ) : ConsumeFromStashError

    data object MissingRecipeBatchWeight : ConsumeFromStashError

    data class FoodNotFound(val foodRef: StashFoodRef) : ConsumeFromStashError
}

class ConsumeFromStashUseCase(
    private val stashRepository: StashRepository,
    private val productRepository: ProductRepository,
    private val recipeRepository: RecipeRepository,
    private val entryRepository: FoodDiaryEntryRepository,
    private val mealRepository: MealRepository,
    private val transactionProvider: TransactionProvider,
    private val dateProvider: DateProvider,
    private val logger: Logger,
) {
    suspend fun consume(
        itemId: StashEntryId,
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

            val recipeBatchWeight = (item.foodRef as? StashFoodRef.Recipe)?.totalWeight
            if (plan.requiresRecipeBatchWeight && (recipeBatchWeight == null || recipeBatchWeight <= EPSILON)) {
                return@withTransaction logger.logAndReturnFailure(
                    tag = TAG,
                    error = ConsumeFromStashError.MissingRecipeBatchWeight,
                    message = { "Stash item ${item.id} has no weight data for weight-equivalent consumption." },
                )
            }

            val now = dateProvider.now()
            val diaryFood =
                item.foodRef.resolveDiaryFood(
                    productRepository = productRepository,
                    recipeRepository = recipeRepository,
                ) ?: return@withTransaction logger.logAndReturnFailure(
                    tag = TAG,
                    error = ConsumeFromStashError.FoodNotFound(item.foodRef),
                    message = { "Food for stash item ${item.id} could not be resolved from ${item.foodRef}." },
                )

            val entryId =
                entryRepository.insert(
                    measurement = plan.diaryMeasurement,
                    mealId = mealId,
                    date = dateOverride ?: now.date,
                    food = diaryFood,
                    createdAt = now,
                )

            val updatedItem = item.copy(measurement = (item.measurement - plan.measurementChange).normalize())
            if (updatedItem.measurement.measurement.rawValue <= EPSILON && updatedItem.canDeleteWhenEmpty()) {
                stashRepository.deleteItem(updatedItem.id)
            } else {
                stashRepository.updateItem(updatedItem)
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

    private fun StashEntry.planConsumption(requestedAmount: StashMeasurement): ConsumptionPlan? =
        when (val foodRef = foodRef) {
            is StashFoodRef.Product -> rawProductPlan(requestedAmount)
            is StashFoodRef.Recipe -> foodRef.recipePlan(measurement, requestedAmount)
        }

    private fun StashEntry.rawProductPlan(requestedAmount: StashMeasurement): ConsumptionPlan? {
        // Same type required — raw products only support same-type consumption.
        if (requestedAmount.type != measurement.type) {
            return null
        }

        return ConsumptionPlan(measurementChange = requestedAmount, diaryMeasurement = requestedAmount.measurement)
    }

    private fun StashFoodRef.Recipe.recipePlan(
        availableMeasurement: StashMeasurement,
        requestedAmount: StashMeasurement,
    ): ConsumptionPlan? {
        // Same-type: requested type matches available type — direct deduction.
        if (requestedAmount.type == availableMeasurement.type) {
            return ConsumptionPlan(measurementChange = requestedAmount, diaryMeasurement = requestedAmount.measurement)
        }

        val requestedWeightType =
            when (requestedAmount.type) {
                MeasurementType.Gram,
                MeasurementType.Milliliter,
                MeasurementType.Ounce,
                MeasurementType.FluidOunce,
                -> true

                else -> false
            }
        val availablePortionType =
            when (availableMeasurement.type) {
                MeasurementType.Serving,
                MeasurementType.Package,
                -> true

                else -> false
            }
        if (!requestedWeightType || !availablePortionType) {
            return null // Unsupported cross-type combination.
        }

        // Convert weight to servings proportionally.
        val requestedWeight = requestedAmount.measurement.rawValue
        val rawValueChange = totalAmount.rawValue * (requestedWeight / totalWeight)
        val measurementChange = StashMeasurement(Measurement.from(availableMeasurement.type, rawValueChange))

        return ConsumptionPlan(
            measurementChange = measurementChange,
            diaryMeasurement = requestedAmount.measurement,
            requiresRecipeBatchWeight = true,
        )
    }

    private fun StashEntry.canDeleteWhenEmpty(): Boolean =
        when (foodRef) {
            is StashFoodRef.Product -> false
            is StashFoodRef.Recipe -> true
        }

    private data class ConsumptionPlan(
        val measurementChange: StashMeasurement,
        val diaryMeasurement: Measurement,
        val requiresRecipeBatchWeight: Boolean = false,
    )

    private companion object {
        const val TAG = "ConsumeFromStashUseCase"
        const val EPSILON = 0.000001
    }
}