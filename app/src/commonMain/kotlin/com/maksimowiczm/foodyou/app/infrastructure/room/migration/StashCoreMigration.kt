package com.maksimowiczm.foodyou.app.infrastructure.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

object StashCoreMigration : Migration(32, 33) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `StashDefinition` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `ownerId` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `createdAtEpochSeconds` INTEGER NOT NULL,
                `ordering` INTEGER NOT NULL
            )
            """
                .trimIndent()
        )
        connection.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_StashDefinition_ownerId`
            ON `StashDefinition` (`ownerId`)
            """
                .trimIndent()
        )
        connection.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS `index_StashDefinition_ownerId_name`
            ON `StashDefinition` (`ownerId`, `name`)
            """
                .trimIndent()
        )

        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `StashEntry` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `stashId` INTEGER NOT NULL,
                `rawValue` REAL NOT NULL,
                `measurementType` INTEGER NOT NULL,
                `createdAtEpochSeconds` INTEGER NOT NULL,
                `foodProductId` INTEGER,
                `foodRecipeId` INTEGER,
                `batchTotalWeight` REAL,
                `batchTotalAmount` REAL,
                `batchTotalAmountType` INTEGER,
                FOREIGN KEY(`stashId`) REFERENCES `StashDefinition`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`foodProductId`) REFERENCES `Product`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`foodRecipeId`) REFERENCES `Recipe`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """
                .trimIndent()
        )
        connection.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_StashEntry_stashId`
            ON `StashEntry` (`stashId`)
            """
                .trimIndent()
        )
        connection.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS `index_StashEntry_stashId_foodProductId_measurementType`
            ON `StashEntry` (`stashId`, `foodProductId`, `measurementType`)
            """
                .trimIndent()
        )
        connection.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS `index_StashEntry_stashId_foodRecipeId_measurementType`
            ON `StashEntry` (`stashId`, `foodRecipeId`, `measurementType`)
            """
                .trimIndent()
        )

        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `StashMovement` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `stashId` INTEGER NOT NULL,
                `itemId` INTEGER NOT NULL,
                `operation` INTEGER NOT NULL,
                `rawValue` REAL NOT NULL,
                `measurementType` INTEGER NOT NULL,
                `linkedDiaryEntryId` INTEGER,
                `note` TEXT,
                `createdAtEpochSeconds` INTEGER NOT NULL,
                FOREIGN KEY(`stashId`) REFERENCES `StashDefinition`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """
                .trimIndent()
        )
        connection.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_StashMovement_stashId`
            ON `StashMovement` (`stashId`)
            """
                .trimIndent()
        )
        connection.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_StashMovement_itemId`
            ON `StashMovement` (`itemId`)
            """
                .trimIndent()
        )
        connection.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_StashMovement_linkedDiaryEntryId`
            ON `StashMovement` (`linkedDiaryEntryId`)
            """
                .trimIndent()
        )
    }
}
