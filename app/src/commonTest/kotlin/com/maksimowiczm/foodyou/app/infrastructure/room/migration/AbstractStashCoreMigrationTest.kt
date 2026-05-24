package com.maksimowiczm.foodyou.app.infrastructure.room.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.execSQL
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

abstract class AbstractStashCoreMigrationTest {
    abstract fun getTestHelper(): MigrationTestHelper

    open fun migrate() {
        val helper = getTestHelper()
        helper.createDatabase(32).close()

        helper.runMigrationsAndValidate(33, listOf(StashCoreMigration)).use { connection ->
            connection.execSQL(
                """
                INSERT INTO Product (id, name, sourceType, isLiquid)
                VALUES (1, 'Chicken breast', 0, 0)
                """
                    .trimIndent()
            )
            connection.execSQL(
                """
                INSERT INTO StashDefinition (id, ownerId, name, createdAtEpochSeconds, ordering)
                VALUES (1, 'local-device', 'Fridge', 1, 0)
                """
                    .trimIndent()
            )
            connection.execSQL(
                """
                INSERT INTO StashItem (
                    id,
                    stashId,
                    snapshotType,
                    rawValue,
                    measurementType,
                    createdAtEpochSeconds,
                    snapshotProductId,
                    snapshotName,
                    snapshotIsLiquid,
                    snapshotSourceType
                )
                VALUES
                    (1, 1, 0, 500.0, 0, 10, 1, 'Chicken breast', 0, 0),
                    (2, 1, 0, 250.0, 0, 20, 1, 'Chicken breast duplicate', 0, 0),
                    (3, 1, 1, 2.0, 2, 30, NULL, 'Tomato soup', 1, 0),
                    (4, 1, 0, 3.0, 2, 40, 999, 'Missing product', 0, 1)
                """
                    .trimIndent()
            )
            connection.execSQL(
                """
                UPDATE StashItem
                SET snapshotNote = 'healed from anonymous dish',
                    snapshotTotalWeight = 800.0,
                    snapshotTotalAmount = 4.0,
                    snapshotTotalAmountType = 2,
                    snapshot_energy = 120.0
                WHERE id = 3
                """
                    .trimIndent()
            )
            connection.execSQL(
                """
                UPDATE StashItem
                SET snapshotBrand = 'Lost Brand',
                    snapshotBarcode = '998877',
                    snapshotNote = 'healed from missing product',
                    snapshotServingWeight = 55.0,
                    snapshot_energy = 345.0
                WHERE id = 4
                """
                    .trimIndent()
            )
            connection.execSQL(
                """
                INSERT INTO StashMovement (
                    id,
                    stashId,
                    itemId,
                    operation,
                    rawValue,
                    measurementType,
                    linkedDiaryEntryId,
                    note,
                    createdAtEpochSeconds
                )
                VALUES
                    (1, 1, 2, 0, 250.0, 0, 42, 'merged duplicate', 21),
                    (2, 1, 3, 0, 2.0, 2, NULL, 'anonymous dish', 31)
                """
                    .trimIndent()
            )
        }

        helper.runMigrationsAndValidate(34, listOf(StashMeasurementMigration)).use { connection ->
            connection.prepare("SELECT COUNT(*) FROM StashEntry").use { statement ->
                statement.step()
                assertEquals(3L, statement.getLong(0))
            }
            connection.prepare(
                """
                SELECT COUNT(*)
                FROM sqlite_master
                WHERE type = 'table'
                  AND name = 'StashItem'
                """
                    .trimIndent()
            ).use { statement ->
                statement.step()
                assertEquals(0L, statement.getLong(0))
            }

            connection.prepare(
                """
                SELECT id, rawValue, foodProductId, foodRecipeId, batchTotalWeight, batchTotalAmount
                FROM StashEntry
                WHERE stashId = 1
                  AND foodProductId = 1
                  AND measurementType = 0
                """
                    .trimIndent()
            ).use { statement ->
                statement.step()
                assertEquals(1L, statement.getLong(0))
                assertEquals(750.0, statement.getDouble(1))
                assertEquals(1L, statement.getLong(2))
                assertTrue(statement.isNull(3))
                assertTrue(statement.isNull(4))
                assertTrue(statement.isNull(5))
            }

            connection.prepare(
                """
                SELECT sourceType, name, note, isLiquid, packageWeight, energy
                FROM Product
                WHERE id = (SELECT foodProductId FROM StashEntry WHERE id = 3)
                """
                    .trimIndent()
            ).use { statement ->
                statement.step()
                assertEquals(0L, statement.getLong(0))
                assertEquals("Tomato soup", statement.getText(1))
                assertEquals("healed from anonymous dish", statement.getText(2))
                assertEquals(1L, statement.getLong(3))
                assertEquals(800.0, statement.getDouble(4))
                assertEquals(120.0, statement.getDouble(5))
            }

            connection.prepare(
                """
                SELECT sourceType, name, brand, barcode, note, servingWeight, energy
                FROM Product
                WHERE id = (SELECT foodProductId FROM StashEntry WHERE id = 4)
                """
                    .trimIndent()
            ).use { statement ->
                statement.step()
                assertEquals(0L, statement.getLong(0))
                assertEquals("Missing product", statement.getText(1))
                assertEquals("Lost Brand", statement.getText(2))
                assertEquals("998877", statement.getText(3))
                assertEquals("healed from missing product", statement.getText(4))
                assertEquals(55.0, statement.getDouble(5))
                assertEquals(345.0, statement.getDouble(6))
            }

            connection.prepare(
                """
                SELECT itemId, linkedDiaryEntryId
                FROM StashMovement
                WHERE note = 'merged duplicate'
                """
                    .trimIndent()
            ).use { statement ->
                statement.step()
                assertEquals(1L, statement.getLong(0))
                assertEquals(42L, statement.getLong(1))
            }
            connection.prepare(
                """
                SELECT itemId
                FROM StashMovement
                WHERE note = 'anonymous dish'
                """
                    .trimIndent()
            ).use { statement ->
                statement.step()
                assertEquals(3L, statement.getLong(0))
            }

            connection.prepare(
                """
                SELECT COUNT(*)
                FROM StashEntry
                WHERE foodRecipeId IS NOT NULL
                """
                    .trimIndent()
            ).use { statement ->
                statement.step()
                assertEquals(0L, statement.getLong(0))
            }

            connection.prepare(
                """
                SELECT COUNT(*)
                FROM StashEntry
                WHERE batchTotalWeight IS NOT NULL
                   OR batchTotalAmount IS NOT NULL
                   OR batchTotalAmountType IS NOT NULL
                """
                    .trimIndent()
            ).use { statement ->
                statement.step()
                assertEquals(0L, statement.getLong(0))
            }

            connection.prepare(
                """
                SELECT COUNT(*)
                FROM Product
                WHERE sourceType = 0
                  AND id > 1
                """
                    .trimIndent()
            ).use { statement ->
                statement.step()
                assertEquals(2L, statement.getLong(0))
            }

            connection.prepare(
                """
                SELECT COUNT(*)
                FROM StashEntry
                WHERE foodProductId IS NULL
                """
                    .trimIndent()
            ).use { statement ->
                statement.step()
                assertFalse(statement.getLong(0) > 0)
            }
        }
    }
}
