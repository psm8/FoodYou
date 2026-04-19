package com.maksimowiczm.foodyou.app.infrastructure.room.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.execSQL
import kotlin.test.assertEquals
import kotlin.test.assertTrue

abstract class AbstractStashItemMeasurementMigrationTest {
    abstract fun getTestHelper(): MigrationTestHelper

    open fun migrate() {
        val helper = getTestHelper()
        helper.createDatabase(34).use { connection ->
            connection.execSQL(
                """
                INSERT INTO StashDefinition (ownerId, name, createdAtEpochSeconds, ordering)
                VALUES ('local-device', 'Pantry', 1, 0)
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
                    snapshotIsLiquid,
                    snapshotBrand,
                    snapshotBarcode,
                    snapshotSourceType,
                    snapshotSourceUrl,
                    snapshotServingWeight,
                    snapshotTotalWeight
                )
                VALUES (1, 0, 1500.0, 0, 2, 7, 'Skyr', 0, 'FoodYou', '1234567890', 0, NULL, 250.0, 1000.0)
                """
                    .trimIndent()
            )
        }

        helper.runMigrationsAndValidate(35, listOf(StashItemMeasurementMigration)).use { connection
            ->
            connection
                .prepare("SELECT measurementType, measurementRawValue FROM StashItem WHERE id = 1")
                .use { statement ->
                    statement.step()
                    assertTrue(statement.isNull(0))
                    assertTrue(statement.isNull(1))
                }

            connection.execSQL(
                """
                UPDATE StashItem
                SET measurementType = 1,
                    measurementRawValue = 1.5
                WHERE id = 1
                """
                    .trimIndent()
            )

            connection
                .prepare("SELECT measurementType, measurementRawValue FROM StashItem WHERE id = 1")
                .use { statement ->
                    statement.step()
                    assertEquals(1, statement.getLong(0))
                    assertEquals(1.5, statement.getDouble(1))
                }
        }
    }
}
