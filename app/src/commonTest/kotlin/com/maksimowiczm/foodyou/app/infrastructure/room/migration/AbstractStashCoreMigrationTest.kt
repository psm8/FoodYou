package com.maksimowiczm.foodyou.app.infrastructure.room.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.execSQL
import kotlin.test.assertEquals

abstract class AbstractStashCoreMigrationTest {
    abstract fun getTestHelper(): MigrationTestHelper

    open fun migrate() {
        val helper = getTestHelper()
        helper.createDatabase(32).close()

        helper.runMigrationsAndValidate(33, listOf(StashCoreMigration)).use { connection ->
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
                    rawValue,
                    measurementType,
                    createdAtEpochSeconds,
                    snapshotName,
                    snapshotIsLiquid,
                    snapshotSourceType
                )
                VALUES (1, 0, 500.0, 0, 2, 'Ham', 0, 0)
                """
                    .trimIndent()
            )
            connection.execSQL(
                """
                INSERT INTO StashMovement (
                    stashId,
                    itemId,
                    operation,
                    rawValue,
                    measurementType,
                    linkedDiaryEntryId,
                    note,
                    createdAtEpochSeconds
                )
                VALUES (1, 1, 0, 500.0, 0, 42, 'test note', 3)
                """
                    .trimIndent()
            )

            connection.prepare("SELECT COUNT(*) FROM StashDefinition").use { statement ->
                statement.step()
                assertEquals(1, statement.getLong(0))
            }
            connection.prepare("SELECT COUNT(*) FROM StashItem").use { statement ->
                statement.step()
                assertEquals(1, statement.getLong(0))
            }
            connection.prepare("SELECT COUNT(*) FROM StashMovement").use { statement ->
                statement.step()
                assertEquals(1, statement.getLong(0))
            }
            connection.prepare("SELECT linkedDiaryEntryId FROM StashMovement").use { statement ->
                statement.step()
                assertEquals(42, statement.getLong(0))
            }
            connection.prepare("SELECT note FROM StashMovement").use { statement ->
                statement.step()
                assertEquals("test note", statement.getText(0))
            }
        }
    }
}
