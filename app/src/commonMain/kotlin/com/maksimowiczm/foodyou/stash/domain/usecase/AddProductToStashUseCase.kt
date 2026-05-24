package com.maksimowiczm.foodyou.stash.domain.usecase

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.common.log.logAndReturnFailure
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinition
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntry
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.StashFoodRef
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import com.maksimowiczm.foodyou.stash.domain.entity.StashName
import com.maksimowiczm.foodyou.stash.domain.repository.StashOwnerProvider
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import kotlinx.coroutines.flow.first

sealed interface AddProductToStashError {
    data class ProductNotFound(val id: FoodId.Product) : AddProductToStashError

    data class StashNotFound(val id: StashDefinitionId) : AddProductToStashError

    data object StashSelectionRequired : AddProductToStashError

    data object InvalidMeasurement : AddProductToStashError
}

data class AddProductToStashResult(val stashId: StashDefinitionId, val itemId: StashEntryId)

class AddProductToStashUseCase(
    private val productRepository: ProductRepository,
    private val stashRepository: StashRepository,
    private val stashOwnerProvider: StashOwnerProvider,
    private val transactionProvider: TransactionProvider,
    private val dateProvider: DateProvider,
    private val logger: Logger,
) {
    suspend fun add(
        productId: FoodId.Product,
        measurement: Measurement,
        stashId: StashDefinitionId? = null,
    ): Result<AddProductToStashResult, AddProductToStashError> {
        val product = productRepository.observeProduct(productId).first()
        if (product == null) {
            return logger.logAndReturnFailure(
                tag = TAG,
                error = AddProductToStashError.ProductNotFound(productId),
                message = { "Product with id $productId not found." },
            )
        }

        val stashMeasurement = product.toStashMeasurementOrNull(measurement)
        if (stashMeasurement == null) {
            return logger.logAndReturnFailure(
                tag = TAG,
                error = AddProductToStashError.InvalidMeasurement,
                message = { "Product ${product.id} does not support measurement $measurement." },
            )
        }

        return transactionProvider.withTransaction {
            val now = dateProvider.now()
            val ownerId = stashOwnerProvider.current()
            val stashes = stashRepository.observeStashes(ownerId).first()
            val targetStash =
                when {
                    stashId != null ->
                        stashes.firstOrNull { it.id == stashId }
                            ?: return@withTransaction logger.logAndReturnFailure(
                                tag = TAG,
                                error = AddProductToStashError.StashNotFound(stashId),
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
                            error = AddProductToStashError.StashSelectionRequired,
                            message = {
                                "A stash must be selected when more than one stash exists."
                            },
                        )
                }

            val existingItem =
                stashRepository.observeStashContents(targetStash.id).first().firstOrNull {
                    val foodRef = it.foodRef
                    foodRef is StashFoodRef.Product &&
                        foodRef.productId == product.id &&
                        it.measurement.type == stashMeasurement.type
                }
            val mergeCandidate = existingItem
            val itemId =
                if (mergeCandidate == null) {
                    stashRepository.insertItem(
                        StashEntry.new(
                            stashId = targetStash.id,
                            foodRef = StashFoodRef.Product(product.id),
                            measurement = stashMeasurement,
                            createdAt = now,
                        )
                    )
                } else {
                    stashRepository.updateItem(
                        mergeCandidate.copy(
                            measurement = mergeCandidate.measurement + stashMeasurement,
                        )
                    )
                    mergeCandidate.id
                }
            stashRepository.insertMovement(
                StashMovement.new(
                    stashId = targetStash.id,
                    itemId = itemId,
                    operation = StashMovementOperation.Purchase,
                    measurementChange = stashMeasurement,
                    linkedDiaryEntryId = null,
                    createdAt = now,
                )
            )

            Ok(AddProductToStashResult(stashId = targetStash.id, itemId = itemId))
        }
    }

    private companion object {
        const val TAG = "AddProductToStashUseCase"
        const val DEFAULT_STASH_NAME = "Stash"
    }
}
