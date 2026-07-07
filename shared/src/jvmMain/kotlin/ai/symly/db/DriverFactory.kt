package ai.symly.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

object DriverFactory {
    fun createDriver(filePath: String?): SqlDriver {
        val path = filePath ?: ":memory:"
        val driver = JdbcSqliteDriver("jdbc:sqlite:$path")
        if (filePath != null) {
            val file = File(filePath)
            if (!file.exists()) {
                GestureDatabase.Schema.create(driver)
            } else {
                migrateLegacyPadding(driver)
            }
        } else {
            GestureDatabase.Schema.create(driver)
        }
        return driver
    }

    /** Migrate single paddingMs column to separate pre/post padding. */
    private fun migrateLegacyPadding(driver: SqlDriver) {
        migrateRecordingPadding(driver)
        migrateSampleSetPadding(driver)
    }

    private fun migrateRecordingPadding(driver: SqlDriver) {
        if (!tableExists(driver, "Recording")) return

        if (tableHasColumn(driver, "Recording", "paddingMs")) {
            if (!tableHasColumn(driver, "Recording", "prePaddingMs")) {
                driver.execute(null, "ALTER TABLE Recording ADD COLUMN prePaddingMs INTEGER NOT NULL DEFAULT 0", 0)
                driver.execute(null, "ALTER TABLE Recording ADD COLUMN postPaddingMs INTEGER NOT NULL DEFAULT 0", 0)
                driver.execute(
                    null,
                    "UPDATE Recording SET prePaddingMs = paddingMs, postPaddingMs = paddingMs",
                    0
                )
            }
            dropLegacyPaddingColumn(driver, "Recording") {
                rebuildRecordingTableWithoutLegacyPadding(driver)
            }
        } else if (!tableHasColumn(driver, "Recording", "prePaddingMs")) {
            driver.execute(null, "ALTER TABLE Recording ADD COLUMN prePaddingMs INTEGER NOT NULL DEFAULT 20", 0)
            driver.execute(null, "ALTER TABLE Recording ADD COLUMN postPaddingMs INTEGER NOT NULL DEFAULT 20", 0)
        }
    }

    private fun migrateSampleSetPadding(driver: SqlDriver) {
        if (!tableExists(driver, "SampleSet")) return

        if (tableHasColumn(driver, "SampleSet", "paddingMs")) {
            if (!tableHasColumn(driver, "SampleSet", "prePaddingMs")) {
                driver.execute(null, "ALTER TABLE SampleSet ADD COLUMN prePaddingMs INTEGER NOT NULL DEFAULT 0", 0)
                driver.execute(null, "ALTER TABLE SampleSet ADD COLUMN postPaddingMs INTEGER NOT NULL DEFAULT 0", 0)
                driver.execute(
                    null,
                    "UPDATE SampleSet SET prePaddingMs = paddingMs, postPaddingMs = paddingMs",
                    0
                )
            }
            dropLegacyPaddingColumn(driver, "SampleSet") {
                rebuildSampleSetTableWithoutLegacyPadding(driver)
            }
        } else if (!tableHasColumn(driver, "SampleSet", "prePaddingMs")) {
            driver.execute(null, "ALTER TABLE SampleSet ADD COLUMN prePaddingMs INTEGER NOT NULL DEFAULT 0", 0)
            driver.execute(null, "ALTER TABLE SampleSet ADD COLUMN postPaddingMs INTEGER NOT NULL DEFAULT 0", 0)
        }
    }

    private fun dropLegacyPaddingColumn(driver: SqlDriver, table: String, rebuild: () -> Unit) {
        try {
            driver.execute(null, "ALTER TABLE $table DROP COLUMN paddingMs", 0)
        } catch (_: Exception) {
            rebuild()
        }
    }

    private fun rebuildRecordingTableWithoutLegacyPadding(driver: SqlDriver) {
        withForeignKeysDisabled(driver) {
            driver.execute(null, """
                CREATE TABLE IF NOT EXISTS Recording_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    gestureId TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    durationMs INTEGER NOT NULL,
                    prePaddingMs INTEGER NOT NULL,
                    postPaddingMs INTEGER NOT NULL,
                    sourceCaptureId TEXT,
                    sampleSetId TEXT,
                    offsetMs INTEGER,
                    FOREIGN KEY (gestureId) REFERENCES Gesture(id) ON DELETE CASCADE
                )
            """.trimIndent(), 0)
            driver.execute(null, """
                INSERT INTO Recording_new (
                    id, gestureId, timestamp, durationMs,
                    prePaddingMs, postPaddingMs,
                    sourceCaptureId, sampleSetId, offsetMs
                )
                SELECT
                    id, gestureId, timestamp, durationMs,
                    prePaddingMs, postPaddingMs,
                    sourceCaptureId, sampleSetId, offsetMs
                FROM Recording
            """.trimIndent(), 0)
            driver.execute(null, "DROP TABLE Recording", 0)
            driver.execute(null, "ALTER TABLE Recording_new RENAME TO Recording", 0)
        }
    }

    private fun rebuildSampleSetTableWithoutLegacyPadding(driver: SqlDriver) {
        withForeignKeysDisabled(driver) {
            driver.execute(null, """
                CREATE TABLE IF NOT EXISTS SampleSet_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    gestureId TEXT NOT NULL,
                    sourceCaptureId TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    strategy TEXT NOT NULL,
                    sampleMs INTEGER NOT NULL,
                    prePaddingMs INTEGER NOT NULL,
                    postPaddingMs INTEGER NOT NULL,
                    stepMs INTEGER,
                    randomCount INTEGER,
                    FOREIGN KEY (gestureId) REFERENCES Gesture(id) ON DELETE CASCADE,
                    FOREIGN KEY (sourceCaptureId) REFERENCES ContinuousCapture(id) ON DELETE CASCADE
                )
            """.trimIndent(), 0)
            driver.execute(null, """
                INSERT INTO SampleSet_new (
                    id, gestureId, sourceCaptureId, timestamp, strategy,
                    sampleMs, prePaddingMs, postPaddingMs, stepMs, randomCount
                )
                SELECT
                    id, gestureId, sourceCaptureId, timestamp, strategy,
                    sampleMs, prePaddingMs, postPaddingMs, stepMs, randomCount
                FROM SampleSet
            """.trimIndent(), 0)
            driver.execute(null, "DROP TABLE SampleSet", 0)
            driver.execute(null, "ALTER TABLE SampleSet_new RENAME TO SampleSet", 0)
        }
    }

    private fun withForeignKeysDisabled(driver: SqlDriver, block: () -> Unit) {
        driver.execute(null, "PRAGMA foreign_keys=OFF", 0)
        try {
            block()
        } finally {
            driver.execute(null, "PRAGMA foreign_keys=ON", 0)
        }
    }

    private fun tableExists(driver: SqlDriver, table: String): Boolean {
        var found = false
        driver.executeQuery(
            identifier = null,
            sql = "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            parameters = 1,
            binders = {
                bindString(0, table)
            },
            mapper = { cursor ->
                if (cursor.next().value) found = true
                app.cash.sqldelight.db.QueryResult.Unit
            }
        )
        return found
    }

    private fun tableHasColumn(driver: SqlDriver, table: String, column: String): Boolean {
        var found = false
        driver.executeQuery(
            identifier = null,
            sql = "PRAGMA table_info($table)",
            parameters = 0,
            binders = null,
            mapper = { cursor ->
                while (cursor.next().value) {
                    if (cursor.getString(1) == column) found = true
                }
                app.cash.sqldelight.db.QueryResult.Unit
            }
        )
        return found
    }
}
