package com.maksimowiczm.foodyou.app.infrastructure.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

object StashItemMeasurementMigration : Migration(35, 36) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            ALTER TABLE `StashItem`
            ADD COLUMN `rawMeasurementType` INTEGER
            """
                .trimIndent()
        )
        connection.execSQL(
            """
            ALTER TABLE `StashItem`
            ADD COLUMN `rawMeasurementValue` REAL
            """
                .trimIndent()
        )
    }
}
