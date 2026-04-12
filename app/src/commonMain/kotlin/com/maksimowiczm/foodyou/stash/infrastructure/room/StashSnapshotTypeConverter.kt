package com.maksimowiczm.foodyou.stash.infrastructure.room

import androidx.room.TypeConverter

@Suppress("unused")
internal class StashSnapshotTypeConverter {
    @TypeConverter
    fun fromSnapshotType(type: StashItemSnapshotType): Int =
        when (type) {
            StashItemSnapshotType.RawProduct -> StashSnapshotTypeSQLConstants.RAW_PRODUCT
            StashItemSnapshotType.AnonymousDish -> StashSnapshotTypeSQLConstants.ANONYMOUS_DISH
        }

    @TypeConverter
    fun toSnapshotType(value: Int): StashItemSnapshotType =
        when (value) {
            StashSnapshotTypeSQLConstants.RAW_PRODUCT -> StashItemSnapshotType.RawProduct
            StashSnapshotTypeSQLConstants.ANONYMOUS_DISH -> StashItemSnapshotType.AnonymousDish
            else -> error("Unknown stash snapshot type: $value")
        }
}

internal object StashSnapshotTypeSQLConstants {
    const val RAW_PRODUCT = 0
    const val ANONYMOUS_DISH = 1
}
