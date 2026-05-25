package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.common.log.logAndReturnFailure
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinition
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntry
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.StashFoodRef
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import com.maksimowiczm.foodyou.stash.domain.entity.StashName
import com.maksimowiczm.foodyou.stash.domain.repository.StashOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import kotlinx.coroutines.flow.first

sealed interface CreateManualStashSnapshotError {
    data object InvalidName : CreateManualStashSnapshotError

    data object InvalidMeasurement : CreateManualStashSnapshotError

    data class StashNotFound(val id: StashDefinitionId) : CreateManualStashSnapshotError

    data object StashSelectionRequired : CreateManualStashSnapshotError
}

data class CreateManualStashSnapshotResult(val stashId: StashDefinitionId, val itemId: StashEntryId)

class CreateManualStashSnapshotUseCase(
    private val productRepository: ProductRepository,
    private val stashRepository: StashRepository,
    private val stashOwnerProvider: StashOwnerProvider,
    private val transactionProvider: TransactionProvider,
    private val dateProvider: DateProvider,
    private val logger: Logger,
) {
    suspend fun create(
        name: String,
        nutritionFacts: NutritionFacts,
        measurement: Measurement,
        stashId: StashDefinitionId? = null,
    ): Result<CreateManualStashSnapshotResult, CreateManualStashSnapshotError> {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) {
            return logger.logAndReturnFailure(
                tag = TAG,
                error = CreateManualStashSnapshotError.InvalidName,
                message = { "Manual quick add product name cannot be empty." },
            )
        }

        val stashMeasurement = measurement.toManualQuickAddMeasurementOrNull()
        if (stashMeasurement == null) {
            return logger.logAndReturnFailure(
                tag = TAG,
                error = CreateManualStashSnapshotError.InvalidMeasurement,
                message = { "Manual quick add does not support measurement $measurement." },
            )
        }

        return transactionProvider.withTransaction {
            val ownerId = stashOwnerProvider.current()
            val now = dateProvider.now()
            val stashes = stashRepository.observeStashes(ownerId).first()
            val targetStash =
                when {
                    stashId != null ->
                        stashes.firstOrNull { it.id == stashId }
                            ?: return@withTransaction logger.logAndReturnFailure(
                                tag = TAG,
                                error = CreateManualStashSnapshotError.StashNotFound(stashId),
                                message = { "Stash with id $stashId not found." },
                            )

                    stashes.isEmpty() -> {
                        val defaultStash =
                            StashDefinition.new(
                                ownerId = ownerId,
                                name = StashName.from(DEFAULT_STASH_NAME),
                                createdAt = now,
                                ordering = 0,
                            )
                        val createdStashId = stashRepository.insertStash(defaultStash)
                        defaultStash.copy(id = createdStashId)
                    }

                    stashes.size == 1 -> stashes.single()

                    else ->
                        return@withTransaction logger.logAndReturnFailure(
                            tag = TAG,
                            error = CreateManualStashSnapshotError.StashSelectionRequired,
                            message = { "A stash must be selected when more than one stash exists." },
                        )
                }

            val productId =
                productRepository.insertProduct(
                    name = normalizedName,
                    brand = null,
                    barcode = null,
                    note = null,
                    isLiquid = measurement.isLiquidMeasurement(),
                    packageWeight = null,
                    servingWeight = null,
                    source = FoodSource(type = FoodSource.Type.User),
                    nutritionFacts = nutritionFacts,
                )
            val itemId =
                stashRepository.insertItem(
                    StashEntry.new(
                        stashId = targetStash.id,
                        foodRef = StashFoodRef.Product(productId),
                        measurement = stashMeasurement,
                        createdAt = now,
                    )
                )
            stashRepository.insertMovement(
                StashMovement.new(
                    stashId = targetStash.id,
                    itemId = itemId,
                    operation = StashMovementOperation.ManualQuickAdd,
                    measurementChange = stashMeasurement,
                    linkedDiaryEntryId = null,
                    createdAt = now,
                )
            )
            Ok(CreateManualStashSnapshotResult(stashId = targetStash.id, itemId = itemId))
        }
    }

    private fun Measurement.toManualQuickAddMeasurementOrNull(): StashMeasurement? {
        if (!rawValue.isFinite() || rawValue <= 0.0) {
            return null
        }

        return when (this) {
            is Measurement.Gram,
            is Measurement.Milliliter,
            is Measurement.Ounce,
            is Measurement.FluidOunce,
            -> StashMeasurement(this)
            is Measurement.Package,
            is Measurement.Serving,
            -> null
        }
    }

    private fun Measurement.isLiquidMeasurement(): Boolean =
        when (this) {
            is Measurement.Milliliter,
            is Measurement.FluidOunce,
            -> true
            is Measurement.Gram,
            is Measurement.Ounce,
            -> false
            is Measurement.Package,
            is Measurement.Serving,
            -> error("Package and serving measurements are invalid for manual quick add.")
        }

    private companion object {
        const val TAG = "CreateManualStashSnapshotUseCase"
        const val DEFAULT_STASH_NAME = "Stash"
    }
}
