package com.maksimowiczm.foodyou.app.infrastructure.room.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.execSQL
import kotlin.test.assertEquals
import kotlin.test.assertTrue

abstract class AbstractStashMovementMeasurementMigrationTest {
    abstract fun getTestHelper(): MigrationTestHelper

    open fun migrate() {
        val helper = getTestHelper()
        helper.createDatabase(34).use { connection ->
            connection.execSQL(
                """
                INSERT INTO StashDefinition (ownerId, name, createdAtEpochSeconds, ordering)
                VALUES ('local-device', 'Fridge', 1, 0)
                """
                    .trimIndent()
            )
            connection.execSQL(
                """
                INSERT INTO StashMovement (
                    stashId,
                    itemId,
                    operation,
                    quantityChange,
                    quantityUnit,
                    linkedDiaryEntryId,
                    note,
                    createdAtEpochSeconds
                )
                VALUES (1, 1, 0, 500.0, 0, NULL, 'Original note', 3)
                """
                    .trimIndent()
            )
        }

        helper.runMigrationsAndValidate(35, listOf(StashMovementMeasurementMigration)).use {
            connection ->
            connection
                .prepare(
                    """
                    SELECT note, rawMeasurementType, rawMeasurementValue
                    FROM StashMovement
                    WHERE id = 1
                    """
                        .trimIndent()
                )
                .use { statement ->
                    statement.step()
                    assertEquals("Original note", statement.getText(0))
                    assertTrue { statement.isNull(1) }
                    assertTrue { statement.isNull(2) }
                }

            connection.execSQL(
                """
                UPDATE StashMovement
                SET rawMeasurementType = 5,
                    rawMeasurementValue = 1.5
                WHERE id = 1
                """
                    .trimIndent()
            )

            connection
                .prepare(
                    """
                    SELECT rawMeasurementType, rawMeasurementValue
                    FROM StashMovement
                    WHERE id = 1
                    """
                        .trimIndent()
                )
                .use { statement ->
                    statement.step()
                    assertEquals(5L, statement.getLong(0))
                    assertEquals(1.5, statement.getDouble(1))
                }
        }
    }
}
