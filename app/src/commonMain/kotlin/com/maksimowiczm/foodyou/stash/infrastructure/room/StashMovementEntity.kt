package com.maksimowiczm.foodyou.stash.infrastructure.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.stash.domain.entity.StashMovementOperation
import com.maksimowiczm.foodyou.stash.domain.entity.StashQuantityUnit

@Entity(
    tableName = "StashMovement",
    foreignKeys =
        [
            ForeignKey(
                entity = StashDefinitionEntity::class,
                parentColumns = ["id"],
                childColumns = ["stashId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices =
        [
            Index(value = ["stashId"]),
            Index(value = ["itemId"]),
            Index(value = ["linkedDiaryEntryId"]),
        ],
)
data class StashMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stashId: Long,
    val itemId: Long,
    val operation: StashMovementOperation,
    val quantityChange: Double,
    val quantityUnit: StashQuantityUnit,
    val linkedDiaryEntryId: Long?,
    val note: String?,
    val rawMeasurementType: MeasurementType?,
    val rawMeasurementValue: Double?,
    val createdAtEpochSeconds: Long,
)
