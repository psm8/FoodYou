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
            connection.prepare("SELECT COUNT(*) FROM StashEntry").use { statement ->
                statement.step()
                assertEquals(0L, statement.getLong(0))
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

            val stashEntryColumns = tableInfo(connection, "StashEntry")
            assertTrue(
                stashEntryColumns.containsAll(
                    listOf(
                        "id",
                        "stashId",
                        "rawValue",
                        "measurementType",
                        "createdAtEpochSeconds",
                        "foodProductId",
                        "foodRecipeId",
                        "batchTotalWeight",
                        "batchTotalAmount",
                        "batchTotalAmountType",
                    )
                )
            )
            assertFalse("snapshotType" in stashEntryColumns)

            val stashEntryIndices = indexNames(connection, "StashEntry")
            assertTrue(
                stashEntryIndices.containsAll(
                    listOf(
                        "index_StashEntry_stashId",
                        "index_StashEntry_stashId_foodProductId_measurementType",
                        "index_StashEntry_stashId_foodRecipeId_measurementType",
                    )
                )
            )

            val stashEntryForeignKeys = foreignKeys(connection, "StashEntry")
            assertTrue(
                stashEntryForeignKeys.containsAll(
                    setOf(
                        "stashId->StashDefinition",
                        "foodProductId->Product",
                        "foodRecipeId->Recipe",
                    )
                )
            )
            assertEquals(
                setOf("stashId->StashDefinition"),
                foreignKeys(connection, "StashMovement"),
            )

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
                INSERT INTO StashEntry (
                    id,
                    stashId,
                    rawValue,
                    measurementType,
                    createdAtEpochSeconds,
                    foodProductId,
                    foodRecipeId,
                    batchTotalWeight,
                    batchTotalAmount,
                    batchTotalAmountType
                )
                VALUES (1, 1, 500.0, 0, 10, 1, NULL, NULL, NULL, NULL)
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
                VALUES (1, 1, 1, 0, 500.0, 0, NULL, 'purchase', 11)
                """
                    .trimIndent()
            )
            connection.prepare(
                """
                SELECT rawValue, foodProductId
                FROM StashEntry
                WHERE id = 1
                """
                    .trimIndent()
            ).use { statement ->
                statement.step()
                assertEquals(500.0, statement.getDouble(0))
                assertEquals(1L, statement.getLong(1))
            }
            val duplicateInsert =
                runCatching {
                    connection.execSQL(
                        """
                        INSERT INTO StashEntry (
                            id,
                            stashId,
                            rawValue,
                            measurementType,
                            createdAtEpochSeconds,
                            foodProductId,
                            foodRecipeId,
                            batchTotalWeight,
                            batchTotalAmount,
                            batchTotalAmountType
                        )
                        VALUES (2, 1, 250.0, 0, 20, 1, NULL, NULL, NULL, NULL)
                        """
                            .trimIndent()
                    )
                }
            assertTrue(duplicateInsert.isFailure)
        }
    }

    private fun tableInfo(connection: androidx.sqlite.SQLiteConnection, tableName: String): List<String> {
        val columns = mutableListOf<String>()
        connection.prepare("PRAGMA table_info(`$tableName`)").use { statement ->
            while (statement.step()) {
                columns += statement.getText(1)
            }
        }
        return columns
    }

    private fun indexNames(connection: androidx.sqlite.SQLiteConnection, tableName: String): List<String> {
        val indices = mutableListOf<String>()
        connection.prepare("PRAGMA index_list(`$tableName`)").use { statement ->
            while (statement.step()) {
                indices += statement.getText(1)
            }
        }
        return indices
    }

    private fun foreignKeys(connection: androidx.sqlite.SQLiteConnection, tableName: String): Set<String> {
        val keys = mutableSetOf<String>()
        connection.prepare("PRAGMA foreign_key_list(`$tableName`)").use { statement ->
            while (statement.step()) {
                val referencedTable = statement.getText(2)
                val fromColumn = statement.getText(3)
                keys += "$fromColumn->$referencedTable"
            }
        }
        return keys
    }
}
