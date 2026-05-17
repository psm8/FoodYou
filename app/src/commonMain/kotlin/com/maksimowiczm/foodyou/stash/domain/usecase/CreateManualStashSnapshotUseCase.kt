package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.common.log.logAndReturnFailure
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.stash.domain.entity.RawProductSnapshot
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinition
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashItem
import com.maksimowiczm.foodyou.stash.domain.entity.StashItemId
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

data class CreateManualStashSnapshotResult(val stashId: StashDefinitionId, val itemId: StashItemId)

class CreateManualStashSnapshotUseCase(
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
        if (normalizedName.isBlank()) {
            return logger.logAndReturnFailure(
                tag = TAG,
                error = CreateManualStashSnapshotError.InvalidName,
                message = { "Manual stash snapshot name must not be blank." },
            )
        }

        val intake =
            measurement.toManualQuickAddIntakeOrNull()
                ?: return logger.logAndReturnFailure(
                    tag = TAG,
                    error = CreateManualStashSnapshotError.InvalidMeasurement,
                    message = { "Measurement $measurement is not supported for manual stash quick add." },
                )

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

            val itemId =
                stashRepository.insertItem(
                    StashItem.new(
                        stashId = targetStash.id,
                        snapshot =
                            RawProductSnapshot(
                                productId = null,
                                name = normalizedName,
                                brand = null,
                                barcode = null,
                                note = null,
                                isLiquid = intake.isLiquid,
                                packageWeight = intake.canonicalQuantity.measurement.rawValue,
                                servingWeight = null,
                                source = FoodSource(type = FoodSource.Type.User),
                                nutritionFacts =
                                    nutritionFacts.normalizeForStashAmount(
                                        amount = intake.canonicalQuantity.measurement.rawValue
                                    ),
                            ),
                        measurement = intake.canonicalQuantity,
                        createdAt = now,
                    )
                )

            stashRepository.insertMovement(
                StashMovement.new(
                    stashId = targetStash.id,
                    itemId = itemId,
                    operation = StashMovementOperation.ManualQuickAdd,
                    measurementChange = intake.canonicalQuantity,
                    linkedDiaryEntryId = null,
                    createdAt = now,
                )
            )

            Ok(CreateManualStashSnapshotResult(stashId = targetStash.id, itemId = itemId))
        }
    }

    private fun NutritionFacts.normalizeForStashAmount(amount: Double): NutritionFacts = this / (amount / 100.0)

    private fun Measurement.toManualQuickAddIntakeOrNull(): ManualQuickAddIntake? =
        when (this) {
            is Measurement.Gram ->
                value.takeIf { it > 0.0 }?.let {
                    ManualQuickAddIntake(
                        canonicalQuantity = StashMeasurement.grams(it),
                        isLiquid = false,
                    )
                }
            is Measurement.Ounce ->
                metric.takeIf { it > 0.0 }?.let {
                    ManualQuickAddIntake(
                        canonicalQuantity = StashMeasurement.grams(it),
                        isLiquid = false,
                    )
                }
            is Measurement.Milliliter ->
                value.takeIf { it > 0.0 }?.let {
                    ManualQuickAddIntake(
                        canonicalQuantity = StashMeasurement.milliliters(it),
                        isLiquid = true,
                    )
                }
            is Measurement.FluidOunce ->
                metric.takeIf { it > 0.0 }?.let {
                    ManualQuickAddIntake(
                        canonicalQuantity = StashMeasurement.milliliters(it),
                        isLiquid = true,
                    )
                }
            is Measurement.Package,
            is Measurement.Serving -> null
        }

    private data class ManualQuickAddIntake(
        val canonicalQuantity: StashMeasurement,
        val isLiquid: Boolean,
    )

    private companion object {
        const val TAG = "CreateManualStashSnapshotUseCase"
        const val DEFAULT_STASH_NAME = "Stash"
    }
}

