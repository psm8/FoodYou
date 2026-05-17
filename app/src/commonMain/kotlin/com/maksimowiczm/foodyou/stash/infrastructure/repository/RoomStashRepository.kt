package com.maksimowiczm.foodyou.stash.infrastructure.repository

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.from
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.common.domain.measurement.type
import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.infrastructure.room.toDomain
import com.maksimowiczm.foodyou.common.infrastructure.room.toEntity
import com.maksimowiczm.foodyou.common.infrastructure.room.toEntityNutrients
import com.maksimowiczm.foodyou.common.infrastructure.room.toNutritionFacts
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.stash.domain.entity.AnonymousDishSnapshot
import com.maksimowiczm.foodyou.stash.domain.entity.LinkedDiaryEntryId
import com.maksimowiczm.foodyou.stash.domain.entity.RawProductSnapshot
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinition
import com.maksimowiczm.foodyou.stash.domain.entity.StashDefinitionId
import com.maksimowiczm.foodyou.stash.domain.entity.StashItem
import com.maksimowiczm.foodyou.stash.domain.entity.StashItemId
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovement
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementId
import com.maksimowiczm.foodyou.stash.domain.entity.StashMeasurement
import com.maksimowiczm.foodyou.stash.domain.entity.StashName
import com.maksimowiczm.foodyou.stash.domain.entity.StashOwnerId
import com.maksimowiczm.foodyou.stash.domain.repository.StashRepository
import com.maksimowiczm.foodyou.stash.infrastructure.room.StashDefinitionDao
import com.maksimowiczm.foodyou.stash.infrastructure.room.StashDefinitionEntity
import com.maksimowiczm.foodyou.stash.infrastructure.room.StashItemDao
import com.maksimowiczm.foodyou.stash.infrastructure.room.StashItemEntity
import com.maksimowiczm.foodyou.stash.infrastructure.room.StashItemSnapshotType
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

    override fun observeStashContents(stashId: StashDefinitionId): Flow<List<StashItem>> =
        stashItemDao.observeItems(stashId.value).map { list -> list.map { it.toModel() } }

    override fun observeMovementHistory(stashId: StashDefinitionId): Flow<List<StashMovement>> =
        stashMovementDao.observeMovements(stashId.value).map { list -> list.map { it.toModel() } }

    override suspend fun getItem(id: StashItemId): StashItem? =
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

    override suspend fun insertItem(item: StashItem): StashItemId =
        transactionProvider.withTransaction {
            val id = stashItemDao.insertStashItem(item.toEntity())
            StashItemId(id)
        }

    override suspend fun updateItem(item: StashItem) {
        stashItemDao.upsertStashItem(item.toEntity())
    }

    override suspend fun deleteItem(id: StashItemId) {
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

private fun StashItemEntity.toModel(): StashItem =
    StashItem(
        id = StashItemId(id),
        stashId = StashDefinitionId(stashId),
        snapshot =
            when (snapshotType) {
                StashItemSnapshotType.RawProduct ->
                    RawProductSnapshot(
                        productId = snapshotProductId?.let { FoodId.Product(it) },
                        name = snapshotName,
                        brand = snapshotBrand,
                        barcode = snapshotBarcode,
                        note = snapshotNote,
                        isLiquid = snapshotIsLiquid,
                        packageWeight = snapshotTotalWeight,
                        servingWeight = snapshotServingWeight,
                        source =
                            FoodSource(
                                type = requireNotNull(snapshotSourceType).toDomain(),
                                url = snapshotSourceUrl,
                            ),
                        nutritionFacts = toNutritionFacts(nutrients, vitamins, minerals),
                    )

                StashItemSnapshotType.AnonymousDish ->
                    AnonymousDishSnapshot(
                        name = snapshotName,
                        nutritionFacts = toNutritionFacts(nutrients, vitamins, minerals),
                        note = snapshotNote,
                        isLiquid = snapshotIsLiquid,
                        totalWeight = requireNotNull(snapshotTotalWeight),
                        totalAmount =
                            Measurement.from(
                                requireNotNull(snapshotTotalAmountType),
                                requireNotNull(snapshotTotalAmount),
                            ),
                    )
            },
        measurement = StashMeasurement(Measurement.from(measurementType, rawValue)),
        createdAt = createdAtEpochSeconds.toLocalDateTime(),
    )

private fun StashItem.toEntity(): StashItemEntity {
    val (nutrients, vitamins, minerals) = toEntityNutrients(snapshot.nutritionFacts)

    return when (val snapshot = snapshot) {
        is RawProductSnapshot ->
            StashItemEntity(
                id = id.value,
                stashId = stashId.value,
                snapshotType = StashItemSnapshotType.RawProduct,
                rawValue = measurement.measurement.rawValue,
                measurementType = measurement.measurement.type,
                createdAtEpochSeconds = createdAt.toEpochSeconds(),
                snapshotProductId = snapshot.productId?.id,
                snapshotName = snapshot.name,
                snapshotNote = snapshot.note,
                snapshotIsLiquid = snapshot.isLiquid,
                snapshotBrand = snapshot.brand,
                snapshotBarcode = snapshot.barcode,
                snapshotSourceType = snapshot.source.type.toEntity(),
                snapshotSourceUrl = snapshot.source.url,
                snapshotServingWeight = snapshot.servingWeight,
                snapshotTotalWeight = snapshot.totalWeight,
                snapshotTotalAmount = null,
                snapshotTotalAmountType = null,
                nutrients = nutrients,
                vitamins = vitamins,
                minerals = minerals,
            )

        is AnonymousDishSnapshot ->
            StashItemEntity(
                id = id.value,
                stashId = stashId.value,
                snapshotType = StashItemSnapshotType.AnonymousDish,
                rawValue = measurement.measurement.rawValue,
                measurementType = measurement.measurement.type,
                createdAtEpochSeconds = createdAt.toEpochSeconds(),
                snapshotProductId = null,
                snapshotName = snapshot.name,
                snapshotNote = snapshot.note,
                snapshotIsLiquid = snapshot.isLiquid,
                snapshotBrand = null,
                snapshotBarcode = null,
                snapshotSourceType = null,
                snapshotSourceUrl = null,
                snapshotServingWeight = snapshot.servingWeight,
                snapshotTotalWeight = snapshot.totalWeight,
                snapshotTotalAmount = snapshot.totalAmount.rawValue,
                snapshotTotalAmountType = snapshot.totalAmount.type,
                nutrients = nutrients,
                vitamins = vitamins,
                minerals = minerals,
            )
    }
}

private fun StashMovementEntity.toModel(): StashMovement =
    StashMovement(
        id = StashMovementId(id),
        stashId = StashDefinitionId(stashId),
        itemId = StashItemId(itemId),
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
