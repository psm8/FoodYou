package com.maksimowiczm.foodyou.app.infrastructure.room.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.execSQL
import kotlin.test.assertEquals
import kotlin.test.assertTrue

abstract class AbstractStashItemMeasurementMigrationTest {
    abstract fun getTestHelper(): MigrationTestHelper

    open fun migrate() {
        val helper = getTestHelper()
        helper.createDatabase(35).use { connection ->
            connection.execSQL(
                """
                INSERT INTO StashDefinition (ownerId, name, createdAtEpochSeconds, ordering)
                VALUES ('local-device', 'Fridge', 1, 0)
                """
                    .trimIndent()
            )
            connection.execSQL(
                """
                INSERT INTO StashItem (
                    stashId,
                    snapshotType,
                    quantity,
                    baseUnit,
                    createdAtEpochSeconds,
                    snapshotProductId,
                    snapshotName,
                    snapshotNote,
                    snapshotIsLiquid,
                    snapshotBrand,
                    snapshotBarcode,
                    snapshotSourceType,
                    snapshotSourceUrl,
                    snapshotServingWeight,
                    snapshotTotalWeight,
                    snapshotTotalAmount,
                    snapshotTotalAmountUnit,
                    snapshot_proteins,
                    snapshot_carbohydrates,
                    snapshot_fats,
                    snapshot_energy,
                    snapshot_vitaminA,
                    snapshot_vitaminC,
                    snapshot_calcium,
                    snapshot_iron
                )
                VALUES (
                    1,
                    0,
                    500.0,
                    0,
                    3,
                    10,
                    'Milk',
                    'Fresh',
                    1,
                    'Brand',
                    '123',
                    0,
                    NULL,
                    250.0,
                    1000.0,
                    NULL,
                    NULL,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0
                )
                """
                    .trimIndent()
            )
        }

        helper.runMigrationsAndValidate(36, listOf(StashItemMeasurementMigration)).use { connection
            ->
            connection
                .prepare(
                    """
                    SELECT snapshotName, rawMeasurementType, rawMeasurementValue
                    FROM StashItem
                    WHERE id = 1
                    """
                        .trimIndent()
                )
                .use { statement ->
                    statement.step()
                    assertEquals("Milk", statement.getText(0))
                    assertTrue { statement.isNull(1) }
                    assertTrue { statement.isNull(2) }
                }

            connection.execSQL(
                """
                UPDATE StashItem
                SET rawMeasurementType = 2,
                    rawMeasurementValue = 330.0
                WHERE id = 1
                """
                    .trimIndent()
            )

            connection
                .prepare(
                    """
                    SELECT rawMeasurementType, rawMeasurementValue
                    FROM StashItem
                    WHERE id = 1
                    """
                        .trimIndent()
                )
                .use { statement ->
                    statement.step()
                    assertEquals(2L, statement.getLong(0))
                    assertEquals(330.0, statement.getDouble(1))
                }
        }
    }
}
