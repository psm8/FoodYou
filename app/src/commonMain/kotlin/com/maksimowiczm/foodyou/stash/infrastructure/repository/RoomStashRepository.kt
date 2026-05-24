package com.maksimowiczm.foodyou.stash.infrastructure.repository

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.from
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.common.domain.measurement.type
import com.maksimowiczm.foodyou.common.infrastructure.room.toEntity
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.stash.domain.entity.LinkedDiaryEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinition
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntry
import com.maksimowiczm.foodyou.stash.domain.entity.StashEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.StashFoodRef
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementId
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import com.maksimowiczm.foodyou.stash.domain.entity.StashName
import com.maksimowiczm.foodyou.stash.domain.entity.StashOwnerId
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import com.maksimowiczm.foodyou.stash.infrastructure.room.StashDefinitionDao
import com.maksimowiczm.foodyou.stash.infrastructure.room.StashDefinitionEntity
import com.maksimowiczm.foodyou.stash.infrastructure.room.StashItemDao
import com.maksimowiczm.foodyou.stash.infrastructure.room.StashMeasurementEntity
import com.maksimowiczm.foodyou.stash.infrastructure.room.StashMovementDao
import com.maksimowiczm.foodyou.stash.infrastructure.room.StashMovementEntity
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

internal class RoomStashRepository(
    private val stashDefinitionDao: StashDefinitionDao,
    private val stashItemDao: StashItemDao,
    private val stashMovementDao: StashMovementDao,
    private val transactionProvider: TransactionProvider,
) : StashRepository {
    override fun observeStashes(ownerId: StashOwnerId): Flow<List<StashDefinition>> =
        stashDefinitionDao.observeStashes(ownerId.value).map { list -> list.map { it.toModel() } }

    override fun observeStashContents(stashId: StashDefinitionId): Flow<List<StashEntry>> =
        stashItemDao.observeItems(stashId.value).map { list -> list.map { it.toModel() } }

    override fun observeMovementHistory(stashId: StashDefinitionId): Flow<List<StashMovement>> =
        stashMovementDao.observeMovements(stashId.value).map { list -> list.map { it.toModel() } }

    override suspend fun getItem(id: StashEntryId): StashEntry? =
        stashItemDao.getStashItem(id.value)?.toModel()

    override suspend fun getLinkedDiaryEntryMovements(
        linkedDiaryEntryId: LinkedDiaryEntryId
    ): List<StashMovement> =
        stashMovementDao
            .getLinkedDiaryEntryMovements(linkedDiaryEntryId.value)
            .map(StashMovementEntity::toModel)

    override suspend fun insertStash(stash: StashDefinition): StashDefinitionId =
        transactionProvider.withTransaction {
            val id = stashDefinitionDao.insertStashDefinition(stash.toEntity())
            StashDefinitionId(id)
        }

    override suspend fun updateStash(stash: StashDefinition) {
        stashDefinitionDao.updateStashDefinition(stash.toEntity())
    }

    override suspend fun deleteStash(id: StashDefinitionId) {
        transactionProvider.withTransaction {
            val entity = stashDefinitionDao.getStashDefinition(id.value) ?: return@withTransaction
            stashDefinitionDao.deleteStashDefinition(entity)
        }
    }

    override suspend fun insertItem(item: StashEntry): StashEntryId =
        transactionProvider.withTransaction {
            val id = stashItemDao.insertStashItem(item.toEntity())
            StashEntryId(id)
        }

    override suspend fun updateItem(item: StashEntry) {
        stashItemDao.upsertStashItem(item.toEntity())
    }

    override suspend fun deleteItem(id: StashEntryId) {
        transactionProvider.withTransaction {
            val entity = stashItemDao.getStashItem(id.value) ?: return@withTransaction
            stashItemDao.deleteStashItem(entity)
        }
    }

    override suspend fun insertMovement(movement: StashMovement): StashMovementId =
        transactionProvider.withTransaction {
            val id = stashMovementDao.insertStashMovement(movement.toEntity())
            StashMovementId(id)
        }
}

private fun StashDefinitionEntity.toModel(): StashDefinition =
    StashDefinition(
        id = StashDefinitionId(id),
        ownerId = StashOwnerId(ownerId),
        name = StashName.from(name),
        createdAt = createdAtEpochSeconds.toLocalDateTime(),
        ordering = ordering,
    )

private fun StashDefinition.toEntity(): StashDefinitionEntity =
    StashDefinitionEntity(
        id = id.value,
        ownerId = ownerId.value,
        name = name.value,
        createdAtEpochSeconds = createdAt.toEpochSeconds(),
        ordering = ordering,
    )

private fun StashMeasurementEntity.toModel(): StashEntry =
    StashEntry(
        id = StashEntryId(id),
        stashId = StashDefinitionId(stashId),
        foodRef = toModelFoodRef(),
        measurement = StashMeasurement(Measurement.from(measurementType, rawValue)),
        createdAt = createdAtEpochSeconds.toLocalDateTime(),
    )

private fun StashMeasurementEntity.toModelFoodRef(): StashFoodRef =
    when {
        foodProductId != null && foodRecipeId == null ->
            StashFoodRef.Product(FoodId.Product(foodProductId))

        foodProductId == null && foodRecipeId != null ->
            StashFoodRef.Recipe(
                recipeId = FoodId.Recipe(foodRecipeId),
                totalWeight = requireNotNull(batchTotalWeight),
                totalAmount =
                    Measurement.from(
                        requireNotNull(batchTotalAmountType),
                        requireNotNull(batchTotalAmount),
                    ),
            )

        else ->
            error(
                "StashEntry $id must reference exactly one food target, but foodProductId=$foodProductId and foodRecipeId=$foodRecipeId."
            )
    }

private fun StashEntry.toEntity(): StashMeasurementEntity =
    when (val foodRef = foodRef) {
        is StashFoodRef.Product ->
            StashMeasurementEntity(
                id = id.value,
                stashId = stashId.value,
                rawValue = measurement.measurement.rawValue,
                measurementType = measurement.measurement.type,
                createdAtEpochSeconds = createdAt.toEpochSeconds(),
                foodProductId = foodRef.productId.id,
                foodRecipeId = null,
                batchTotalWeight = null,
                batchTotalAmount = null,
                batchTotalAmountType = null,
            )

        is StashFoodRef.Recipe ->
            StashMeasurementEntity(
                id = id.value,
                stashId = stashId.value,
                rawValue = measurement.measurement.rawValue,
                measurementType = measurement.measurement.type,
                createdAtEpochSeconds = createdAt.toEpochSeconds(),
                foodProductId = null,
                foodRecipeId = foodRef.recipeId.id,
                batchTotalWeight = foodRef.totalWeight,
                batchTotalAmount = foodRef.totalAmount.rawValue,
                batchTotalAmountType = foodRef.totalAmount.type,
            )
    }

private fun StashMovementEntity.toModel(): StashMovement =
    StashMovement(
        id = StashMovementId(id),
        stashId = StashDefinitionId(stashId),
        itemId = StashEntryId(itemId),
        operation = operation,
        measurementChange = StashMeasurement(Measurement.from(measurementType, rawValue)),
        linkedDiaryEntryId = linkedDiaryEntryId?.let(::LinkedDiaryEntryId),
        note = note,
        createdAt = createdAtEpochSeconds.toLocalDateTime(),
    )

private fun StashMovement.toEntity(): StashMovementEntity =
    StashMovementEntity(
        id = id.value,
        stashId = stashId.value,
        itemId = itemId.value,
        operation = operation,
        rawValue = measurementChange.measurement.rawValue,
        measurementType = measurementChange.measurement.type,
        linkedDiaryEntryId = linkedDiaryEntryId?.value,
        note = note,
        createdAtEpochSeconds = createdAt.toEpochSeconds(),
    )

private fun LocalDateTime.toEpochSeconds(): Long =
    toInstant(TimeZone.currentSystemDefault()).epochSeconds

private fun Long.toLocalDateTime(): LocalDateTime =
    Instant.fromEpochSeconds(this).toLocalDateTime(TimeZone.currentSystemDefault())
