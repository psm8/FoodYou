package com.maksimowiczm.foodyou.app.infrastructure.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

object StashMovementNoteMigration : Migration(33, 34) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            ALTER TABLE `StashMovement`
            ADD COLUMN `note` TEXT
            """
                .trimIndent()
        )
    }
}
