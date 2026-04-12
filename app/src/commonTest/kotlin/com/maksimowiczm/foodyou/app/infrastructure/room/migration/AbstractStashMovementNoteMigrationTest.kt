package com.maksimowiczm.foodyou.app.infrastructure.room.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.execSQL
import kotlin.test.assertEquals
import kotlin.test.assertTrue

abstract class AbstractStashMovementNoteMigrationTest {
    abstract fun getTestHelper(): MigrationTestHelper

    open fun migrate() {
        val helper = getTestHelper()
        helper.createDatabase(33).use { connection ->
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
                    createdAtEpochSeconds
                )
                VALUES (1, 1, 0, 500.0, 0, NULL, 3)
                """
                    .trimIndent()
            )
        }

        helper.runMigrationsAndValidate(34, listOf(StashMovementNoteMigration)).use { connection ->
            connection.prepare("SELECT note FROM StashMovement WHERE id = 1").use { statement ->
                statement.step()
                assertTrue { statement.isNull(0) }
            }
            connection.execSQL(
                """
                UPDATE StashMovement
                SET note = 'Adjusted after spoilage'
                WHERE id = 1
                """
                    .trimIndent()
            )
            connection.prepare("SELECT note FROM StashMovement WHERE id = 1").use { statement ->
                statement.step()
                assertEquals("Adjusted after spoilage", statement.getText(0))
            }
        }
    }
}
