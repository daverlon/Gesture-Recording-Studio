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
            }
        } else {
            GestureDatabase.Schema.create(driver)
        }
        return driver
    }
}
