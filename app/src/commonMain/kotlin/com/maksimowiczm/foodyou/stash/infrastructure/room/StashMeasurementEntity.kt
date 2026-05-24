package com.maksimowiczm.foodyou.stash.infrastructure.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.food.infrastructure.room.ProductEntity
import com.maksimowiczm.foodyou.food.infrastructure.room.RecipeEntity

@Entity(
    tableName = "StashEntry",
    foreignKeys =
        [
            ForeignKey(
                entity = StashDefinitionEntity::class,
                parentColumns = ["id"],
                childColumns = ["stashId"],
                onDelete = ForeignKey.CASCADE,
            ),
            ForeignKey(
                entity = ProductEntity::class,
                parentColumns = ["id"],
                childColumns = ["foodProductId"],
                onDelete = ForeignKey.CASCADE,
            ),
            ForeignKey(
                entity = RecipeEntity::class,
                parentColumns = ["id"],
                childColumns = ["foodRecipeId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    indices =
        [
            Index(value = ["stashId"]),
            Index(value = ["stashId", "foodProductId", "measurementType"], unique = true),
            Index(value = ["stashId", "foodRecipeId", "measurementType"], unique = true),
        ],
)
data class StashMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stashId: Long,
    val rawValue: Double,
    val measurementType: MeasurementType,
    val createdAtEpochSeconds: Long,
    val foodProductId: Long?,
    val foodRecipeId: Long?,
    val batchTotalWeight: Double?,
    val batchTotalAmount: Double?,
    val batchTotalAmountType: MeasurementType?,
)
