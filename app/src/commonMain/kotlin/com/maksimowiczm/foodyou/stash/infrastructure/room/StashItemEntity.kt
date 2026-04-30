package com.maksimowiczm.foodyou.stash.infrastructure.room

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.common.infrastructure.room.FoodSourceType
import com.maksimowiczm.foodyou.common.infrastructure.room.Minerals
import com.maksimowiczm.foodyou.common.infrastructure.room.Nutrients
import com.maksimowiczm.foodyou.common.infrastructure.room.Vitamins
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantityUnit

@Entity(
    tableName = "StashItem",
    foreignKeys =
        [
            ForeignKey(
                entity = StashDefinitionEntity::class,
                parentColumns = ["id"],
                childColumns = ["stashId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index(value = ["stashId"])],
)
data class StashItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stashId: Long,
    val snapshotType: StashItemSnapshotType,
    val quantity: Double,
    val baseUnit: StashQuantityUnit,
    val createdAtEpochSeconds: Long,
    val rawMeasurementType: MeasurementType?,
    val rawMeasurementValue: Double?,
    val snapshotProductId: Long?,
    val snapshotName: String,
    val snapshotNote: String?,
    val snapshotIsLiquid: Boolean,
    val snapshotBrand: String?,
    val snapshotBarcode: String?,
    val snapshotSourceType: FoodSourceType?,
    val snapshotSourceUrl: String?,
    val snapshotServingWeight: Double?,
    val snapshotTotalWeight: Double?,
    val snapshotTotalAmount: Double?,
    val snapshotTotalAmountUnit: StashQuantityUnit?,
    @Embedded(prefix = "snapshot_") val nutrients: Nutrients,
    @Embedded(prefix = "snapshot_") val vitamins: Vitamins,
    @Embedded(prefix = "snapshot_") val minerals: Minerals,
)
