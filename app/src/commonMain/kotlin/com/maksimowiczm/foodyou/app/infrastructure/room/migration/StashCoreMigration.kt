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
            CREATE TABLE IF NOT EXISTS `StashItem` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `stashId` INTEGER NOT NULL,
                `snapshotType` INTEGER NOT NULL,
                `rawValue` REAL NOT NULL,
                `measurementType` INTEGER NOT NULL,
                `createdAtEpochSeconds` INTEGER NOT NULL,
                `snapshotProductId` INTEGER,
                `snapshotName` TEXT NOT NULL,
                `snapshotNote` TEXT,
                `snapshotIsLiquid` INTEGER NOT NULL,
                `snapshotBrand` TEXT,
                `snapshotBarcode` TEXT,
                `snapshotSourceType` INTEGER,
                `snapshotSourceUrl` TEXT,
                `snapshotServingWeight` REAL,
                `snapshotTotalWeight` REAL,
                `snapshotTotalAmount` REAL,
                `snapshotTotalAmountType` INTEGER,
                `snapshot_energy` REAL,
                `snapshot_proteins` REAL,
                `snapshot_fats` REAL,
                `snapshot_saturatedFats` REAL,
                `snapshot_transFats` REAL,
                `snapshot_monounsaturatedFats` REAL,
                `snapshot_polyunsaturatedFats` REAL,
                `snapshot_omega3` REAL,
                `snapshot_omega6` REAL,
                `snapshot_carbohydrates` REAL,
                `snapshot_sugars` REAL,
                `snapshot_addedSugars` REAL,
                `snapshot_dietaryFiber` REAL,
                `snapshot_solubleFiber` REAL,
                `snapshot_insolubleFiber` REAL,
                `snapshot_salt` REAL,
                `snapshot_cholesterolMilli` REAL,
                `snapshot_caffeineMilli` REAL,
                `snapshot_vitaminAMicro` REAL,
                `snapshot_vitaminB1Milli` REAL,
                `snapshot_vitaminB2Milli` REAL,
                `snapshot_vitaminB3Milli` REAL,
                `snapshot_vitaminB5Milli` REAL,
                `snapshot_vitaminB6Milli` REAL,
                `snapshot_vitaminB7Micro` REAL,
                `snapshot_vitaminB9Micro` REAL,
                `snapshot_vitaminB12Micro` REAL,
                `snapshot_vitaminCMilli` REAL,
                `snapshot_vitaminDMicro` REAL,
                `snapshot_vitaminEMilli` REAL,
                `snapshot_vitaminKMicro` REAL,
                `snapshot_manganeseMilli` REAL,
                `snapshot_magnesiumMilli` REAL,
                `snapshot_potassiumMilli` REAL,
                `snapshot_calciumMilli` REAL,
                `snapshot_copperMilli` REAL,
                `snapshot_zincMilli` REAL,
                `snapshot_sodiumMilli` REAL,
                `snapshot_ironMilli` REAL,
                `snapshot_phosphorusMilli` REAL,
                `snapshot_seleniumMicro` REAL,
                `snapshot_iodineMicro` REAL,
                `snapshot_chromiumMicro` REAL,
                FOREIGN KEY(`stashId`) REFERENCES `StashDefinition`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """
                .trimIndent()
        )
        connection.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_StashItem_stashId`
            ON `StashItem` (`stashId`)
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
