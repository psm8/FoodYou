package com.maksimowiczm.foodyou.app.infrastructure.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

object StashMeasurementMigration : Migration(33, 34) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `StashItem` RENAME TO `StashItem_legacy`")
        createSyntheticProductSeed(connection)
        insertSyntheticProducts(connection)
        createStashEntryMigrationSource(connection)
        createStashEntryIdMap(connection)
        createStashEntryTable(connection)
        insertMergedEntries(connection)
        remapMovements(connection)
        dropMigrationTables(connection)
    }
}

private fun createSyntheticProductSeed(connection: SQLiteConnection) {
    connection.execSQL(
        """
        CREATE TEMP TABLE `SyntheticProductSeed` AS
        WITH healing_rows AS (
            SELECT
                `id` AS `legacyItemId`,
                CASE
                    WHEN `snapshotType` = 1 THEN 'dish:' || `id`
                    WHEN `snapshotProductId` IS NULL THEN 'missing:' || `id`
                    ELSE 'orphan:' || `snapshotProductId`
                END AS `healingKey`,
                `snapshotName`,
                `snapshotBrand`,
                `snapshotBarcode`,
                `snapshotNote`,
                `snapshotIsLiquid`,
                `snapshotSourceUrl`,
                `snapshotServingWeight`,
                `snapshotTotalWeight`,
                `snapshot_energy`,
                `snapshot_proteins`,
                `snapshot_fats`,
                `snapshot_saturatedFats`,
                `snapshot_transFats`,
                `snapshot_monounsaturatedFats`,
                `snapshot_polyunsaturatedFats`,
                `snapshot_omega3`,
                `snapshot_omega6`,
                `snapshot_carbohydrates`,
                `snapshot_sugars`,
                `snapshot_addedSugars`,
                `snapshot_dietaryFiber`,
                `snapshot_solubleFiber`,
                `snapshot_insolubleFiber`,
                `snapshot_salt`,
                `snapshot_cholesterolMilli`,
                `snapshot_caffeineMilli`,
                `snapshot_vitaminAMicro`,
                `snapshot_vitaminB1Milli`,
                `snapshot_vitaminB2Milli`,
                `snapshot_vitaminB3Milli`,
                `snapshot_vitaminB5Milli`,
                `snapshot_vitaminB6Milli`,
                `snapshot_vitaminB7Micro`,
                `snapshot_vitaminB9Micro`,
                `snapshot_vitaminB12Micro`,
                `snapshot_vitaminCMilli`,
                `snapshot_vitaminDMicro`,
                `snapshot_vitaminEMilli`,
                `snapshot_vitaminKMicro`,
                `snapshot_manganeseMilli`,
                `snapshot_magnesiumMilli`,
                `snapshot_potassiumMilli`,
                `snapshot_calciumMilli`,
                `snapshot_copperMilli`,
                `snapshot_zincMilli`,
                `snapshot_sodiumMilli`,
                `snapshot_ironMilli`,
                `snapshot_phosphorusMilli`,
                `snapshot_seleniumMicro`,
                `snapshot_iodineMicro`,
                `snapshot_chromiumMicro`
            FROM `StashItem_legacy` AS `legacy`
            WHERE
                `snapshotType` = 1
                OR `snapshotProductId` IS NULL
                OR NOT EXISTS (
                    SELECT 1
                    FROM `Product`
                    WHERE `Product`.`id` = `legacy`.`snapshotProductId`
                )
        ),
        healing_groups AS (
            SELECT
                `healingKey`,
                MIN(`legacyItemId`) AS `firstLegacyItemId`
            FROM `healing_rows`
            GROUP BY `healingKey`
        )
        SELECT
            ROW_NUMBER() OVER (ORDER BY `healing_groups`.`firstLegacyItemId`) AS `seq`,
            `healing_rows`.*
        FROM `healing_groups`
        JOIN `healing_rows`
            ON `healing_rows`.`legacyItemId` = `healing_groups`.`firstLegacyItemId`
        ORDER BY `healing_groups`.`firstLegacyItemId`
        """
            .trimIndent()
    )
    connection.execSQL(
        """
        CREATE TEMP TABLE `SyntheticProductBase` AS
        SELECT
            IFNULL(MAX(`id`), 0) AS `baseId`
        FROM `Product`
        """
            .trimIndent()
    )
}

private fun insertSyntheticProducts(connection: SQLiteConnection) {
    connection.execSQL(
        """
        INSERT INTO `Product` (
            `id`,
            `name`,
            `brand`,
            `barcode`,
            `packageWeight`,
            `servingWeight`,
            `note`,
            `sourceType`,
            `sourceUrl`,
            `isLiquid`,
            `energy`,
            `proteins`,
            `fats`,
            `saturatedFats`,
            `transFats`,
            `monounsaturatedFats`,
            `polyunsaturatedFats`,
            `omega3`,
            `omega6`,
            `carbohydrates`,
            `sugars`,
            `addedSugars`,
            `dietaryFiber`,
            `solubleFiber`,
            `insolubleFiber`,
            `salt`,
            `cholesterolMilli`,
            `caffeineMilli`,
            `vitaminAMicro`,
            `vitaminB1Milli`,
            `vitaminB2Milli`,
            `vitaminB3Milli`,
            `vitaminB5Milli`,
            `vitaminB6Milli`,
            `vitaminB7Micro`,
            `vitaminB9Micro`,
            `vitaminB12Micro`,
            `vitaminCMilli`,
            `vitaminDMicro`,
            `vitaminEMilli`,
            `vitaminKMicro`,
            `manganeseMilli`,
            `magnesiumMilli`,
            `potassiumMilli`,
            `calciumMilli`,
            `copperMilli`,
            `zincMilli`,
            `sodiumMilli`,
            `ironMilli`,
            `phosphorusMilli`,
            `seleniumMicro`,
            `iodineMicro`,
            `chromiumMicro`
        )
        SELECT
            (SELECT `baseId` FROM `SyntheticProductBase`) + `seq`,
            `snapshotName`,
            `snapshotBrand`,
            `snapshotBarcode`,
            `snapshotTotalWeight`,
            `snapshotServingWeight`,
            `snapshotNote`,
            0,
            `snapshotSourceUrl`,
            `snapshotIsLiquid`,
            `snapshot_energy`,
            `snapshot_proteins`,
            `snapshot_fats`,
            `snapshot_saturatedFats`,
            `snapshot_transFats`,
            `snapshot_monounsaturatedFats`,
            `snapshot_polyunsaturatedFats`,
            `snapshot_omega3`,
            `snapshot_omega6`,
            `snapshot_carbohydrates`,
            `snapshot_sugars`,
            `snapshot_addedSugars`,
            `snapshot_dietaryFiber`,
            `snapshot_solubleFiber`,
            `snapshot_insolubleFiber`,
            `snapshot_salt`,
            `snapshot_cholesterolMilli`,
            `snapshot_caffeineMilli`,
            `snapshot_vitaminAMicro`,
            `snapshot_vitaminB1Milli`,
            `snapshot_vitaminB2Milli`,
            `snapshot_vitaminB3Milli`,
            `snapshot_vitaminB5Milli`,
            `snapshot_vitaminB6Milli`,
            `snapshot_vitaminB7Micro`,
            `snapshot_vitaminB9Micro`,
            `snapshot_vitaminB12Micro`,
            `snapshot_vitaminCMilli`,
            `snapshot_vitaminDMicro`,
            `snapshot_vitaminEMilli`,
            `snapshot_vitaminKMicro`,
            `snapshot_manganeseMilli`,
            `snapshot_magnesiumMilli`,
            `snapshot_potassiumMilli`,
            `snapshot_calciumMilli`,
            `snapshot_copperMilli`,
            `snapshot_zincMilli`,
            `snapshot_sodiumMilli`,
            `snapshot_ironMilli`,
            `snapshot_phosphorusMilli`,
            `snapshot_seleniumMicro`,
            `snapshot_iodineMicro`,
            `snapshot_chromiumMicro`
        FROM `SyntheticProductSeed`
        ORDER BY `seq`
        """
            .trimIndent()
    )
    connection.execSQL(
        """
        CREATE TEMP TABLE `SyntheticProductMap` AS
        SELECT
            `healingKey`,
            (SELECT `baseId` FROM `SyntheticProductBase`) + `seq` AS `productId`
        FROM `SyntheticProductSeed`
        """
            .trimIndent()
    )
}

private fun createStashEntryMigrationSource(connection: SQLiteConnection) {
    connection.execSQL(
        """
        CREATE TEMP TABLE `StashEntryMigrationSource` AS
        SELECT
            `legacy`.`id` AS `oldId`,
            `legacy`.`stashId`,
            `legacy`.`rawValue`,
            `legacy`.`measurementType`,
            `legacy`.`createdAtEpochSeconds`,
            CASE
                WHEN `legacy`.`snapshotType` = 1 THEN (
                    SELECT `productId`
                    FROM `SyntheticProductMap`
                    WHERE `healingKey` = 'dish:' || `legacy`.`id`
                )
                WHEN `legacy`.`snapshotProductId` IS NULL THEN (
                    SELECT `productId`
                    FROM `SyntheticProductMap`
                    WHERE `healingKey` = 'missing:' || `legacy`.`id`
                )
                WHEN EXISTS (
                    SELECT 1
                    FROM `Product`
                    WHERE `Product`.`id` = `legacy`.`snapshotProductId`
                ) THEN `legacy`.`snapshotProductId`
                ELSE (
                    SELECT `productId`
                    FROM `SyntheticProductMap`
                    WHERE `healingKey` = 'orphan:' || `legacy`.`snapshotProductId`
                )
            END AS `foodProductId`
        FROM `StashItem_legacy` AS `legacy`
        """
            .trimIndent()
    )
}

private fun createStashEntryIdMap(connection: SQLiteConnection) {
    connection.execSQL(
        """
        CREATE TEMP TABLE `StashEntryIdMap` AS
        SELECT
            `oldId`,
            MIN(`oldId`) OVER (
                PARTITION BY `stashId`, `foodProductId`, `measurementType`
            ) AS `newId`
        FROM `StashEntryMigrationSource`
        """
            .trimIndent()
    )
}

private fun createStashEntryTable(connection: SQLiteConnection) {
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
}

private fun insertMergedEntries(connection: SQLiteConnection) {
    connection.execSQL(
        """
        INSERT INTO `StashEntry` (
            `id`,
            `stashId`,
            `rawValue`,
            `measurementType`,
            `createdAtEpochSeconds`,
            `foodProductId`,
            `foodRecipeId`,
            `batchTotalWeight`,
            `batchTotalAmount`,
            `batchTotalAmountType`
        )
        SELECT
            `idMap`.`newId`,
            `source`.`stashId`,
            SUM(`source`.`rawValue`),
            `source`.`measurementType`,
            MIN(`source`.`createdAtEpochSeconds`),
            `source`.`foodProductId`,
            NULL,
            NULL,
            NULL,
            NULL
        FROM `StashEntryMigrationSource` AS `source`
        JOIN `StashEntryIdMap` AS `idMap`
            ON `idMap`.`oldId` = `source`.`oldId`
        GROUP BY
            `idMap`.`newId`,
            `source`.`stashId`,
            `source`.`foodProductId`,
            `source`.`measurementType`
        ORDER BY `idMap`.`newId`
        """
            .trimIndent()
    )
}

private fun remapMovements(connection: SQLiteConnection) {
    connection.execSQL(
        """
        UPDATE `StashMovement`
        SET `itemId` = (
            SELECT `newId`
            FROM `StashEntryIdMap`
            WHERE `oldId` = `StashMovement`.`itemId`
        )
        WHERE EXISTS (
            SELECT 1
            FROM `StashEntryIdMap`
            WHERE `oldId` = `StashMovement`.`itemId`
        )
        """
            .trimIndent()
    )
}

private fun dropMigrationTables(connection: SQLiteConnection) {
    connection.execSQL("DROP TABLE `StashItem_legacy`")
    connection.execSQL("DROP TABLE `StashEntryMigrationSource`")
    connection.execSQL("DROP TABLE `StashEntryIdMap`")
    connection.execSQL("DROP TABLE `SyntheticProductMap`")
    connection.execSQL("DROP TABLE `SyntheticProductSeed`")
    connection.execSQL("DROP TABLE `SyntheticProductBase`")
}
