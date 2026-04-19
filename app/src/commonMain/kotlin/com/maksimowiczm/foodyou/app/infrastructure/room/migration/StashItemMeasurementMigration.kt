package com.maksimowiczm.foodyou.app.infrastructure.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

object StashItemMeasurementMigration : Migration(34, 35) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            ALTER TABLE `StashItem`
            ADD COLUMN `measurementType` INTEGER
            """
                .trimIndent()
        )
        connection.execSQL(
            """
            ALTER TABLE `StashItem`
            ADD COLUMN `measurementRawValue` REAL
            """
                .trimIndent()
        )
    }
}
