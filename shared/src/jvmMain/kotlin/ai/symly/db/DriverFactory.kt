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
        if (!tableHasColumn(driver, "Recording", "prePaddingMs")) {
            if (tableHasColumn(driver, "Recording", "paddingMs")) {
                driver.execute(null, "ALTER TABLE Recording ADD COLUMN prePaddingMs INTEGER NOT NULL DEFAULT 0", 0)
                driver.execute(null, "ALTER TABLE Recording ADD COLUMN postPaddingMs INTEGER NOT NULL DEFAULT 0", 0)
                driver.execute(
                    null,
                    "UPDATE Recording SET prePaddingMs = paddingMs, postPaddingMs = paddingMs",
                    0
                )
            } else {
                // Very old or corrupt — ensure columns exist for new schema
                driver.execute(null, "ALTER TABLE Recording ADD COLUMN prePaddingMs INTEGER NOT NULL DEFAULT 20", 0)
                driver.execute(null, "ALTER TABLE Recording ADD COLUMN postPaddingMs INTEGER NOT NULL DEFAULT 20", 0)
            }
        }
        if (!tableHasColumn(driver, "SampleSet", "prePaddingMs")) {
            if (tableHasColumn(driver, "SampleSet", "paddingMs")) {
                driver.execute(null, "ALTER TABLE SampleSet ADD COLUMN prePaddingMs INTEGER NOT NULL DEFAULT 0", 0)
                driver.execute(null, "ALTER TABLE SampleSet ADD COLUMN postPaddingMs INTEGER NOT NULL DEFAULT 0", 0)
                driver.execute(
                    null,
                    "UPDATE SampleSet SET prePaddingMs = paddingMs, postPaddingMs = paddingMs",
                    0
                )
            }
        }
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
