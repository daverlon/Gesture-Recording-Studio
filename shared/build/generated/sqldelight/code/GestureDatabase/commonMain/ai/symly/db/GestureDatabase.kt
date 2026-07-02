package ai.symly.db

import ai.symly.db.shared.newInstance
import ai.symly.db.shared.schema
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import kotlin.Unit

public interface GestureDatabase : Transacter {
  public val gestureDatabaseQueries: GestureDatabaseQueries

  public companion object {
    public val Schema: SqlSchema<QueryResult.Value<Unit>>
      get() = GestureDatabase::class.schema

    public operator fun invoke(driver: SqlDriver): GestureDatabase =
        GestureDatabase::class.newInstance(driver)
  }
}
