package ai.symly.db.shared

import ai.symly.db.GestureDatabase
import ai.symly.db.GestureDatabaseQueries
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import kotlin.Long
import kotlin.Unit
import kotlin.reflect.KClass

internal val KClass<GestureDatabase>.schema: SqlSchema<QueryResult.Value<Unit>>
  get() = GestureDatabaseImpl.Schema

internal fun KClass<GestureDatabase>.newInstance(driver: SqlDriver): GestureDatabase =
    GestureDatabaseImpl(driver)

private class GestureDatabaseImpl(
  driver: SqlDriver,
) : TransacterImpl(driver), GestureDatabase {
  override val gestureDatabaseQueries: GestureDatabaseQueries = GestureDatabaseQueries(driver)

  public object Schema : SqlSchema<QueryResult.Value<Unit>> {
    override val version: Long
      get() = 1

    override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
      driver.execute(null, """
          |CREATE TABLE IF NOT EXISTS Gesture (
          |    id TEXT NOT NULL PRIMARY KEY,
          |    name TEXT NOT NULL,
          |    recordMode TEXT NOT NULL
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE IF NOT EXISTS Recording (
          |    id TEXT NOT NULL PRIMARY KEY,
          |    gestureId TEXT NOT NULL,
          |    timestamp INTEGER NOT NULL,
          |    durationMs INTEGER NOT NULL,
          |    paddingMs INTEGER NOT NULL,
          |    sourceCaptureId TEXT,
          |    sampleSetId TEXT,
          |    offsetMs INTEGER,
          |    FOREIGN KEY (gestureId) REFERENCES Gesture(id) ON DELETE CASCADE
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE IF NOT EXISTS ImuSample (
          |    id INTEGER PRIMARY KEY AUTOINCREMENT,
          |    recordingId TEXT NOT NULL,
          |    sequence INTEGER NOT NULL,
          |    ax REAL NOT NULL,
          |    ay REAL NOT NULL,
          |    az REAL NOT NULL,
          |    gx REAL NOT NULL,
          |    gy REAL NOT NULL,
          |    gz REAL NOT NULL,
          |    roll REAL NOT NULL,
          |    pitch REAL NOT NULL,
          |    yaw REAL NOT NULL,
          |    FOREIGN KEY (recordingId) REFERENCES Recording(id) ON DELETE CASCADE
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE IF NOT EXISTS ContinuousCapture (
          |    id TEXT NOT NULL PRIMARY KEY,
          |    gestureId TEXT NOT NULL,
          |    timestamp INTEGER NOT NULL,
          |    durationMs INTEGER NOT NULL,
          |    FOREIGN KEY (gestureId) REFERENCES Gesture(id) ON DELETE CASCADE
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE IF NOT EXISTS ContinuousCaptureSample (
          |    id INTEGER PRIMARY KEY AUTOINCREMENT,
          |    captureId TEXT NOT NULL,
          |    sequence INTEGER NOT NULL,
          |    ax REAL NOT NULL,
          |    ay REAL NOT NULL,
          |    az REAL NOT NULL,
          |    gx REAL NOT NULL,
          |    gy REAL NOT NULL,
          |    gz REAL NOT NULL,
          |    roll REAL NOT NULL,
          |    pitch REAL NOT NULL,
          |    yaw REAL NOT NULL,
          |    FOREIGN KEY (captureId) REFERENCES ContinuousCapture(id) ON DELETE CASCADE
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE IF NOT EXISTS SampleSet (
          |    id TEXT NOT NULL PRIMARY KEY,
          |    gestureId TEXT NOT NULL,
          |    sourceCaptureId TEXT NOT NULL,
          |    timestamp INTEGER NOT NULL,
          |    strategy TEXT NOT NULL,
          |    sampleMs INTEGER NOT NULL,
          |    paddingMs INTEGER NOT NULL,
          |    stepMs INTEGER,
          |    randomCount INTEGER,
          |    FOREIGN KEY (gestureId) REFERENCES Gesture(id) ON DELETE CASCADE,
          |    FOREIGN KEY (sourceCaptureId) REFERENCES ContinuousCapture(id) ON DELETE CASCADE
          |)
          """.trimMargin(), 0)
      return QueryResult.Unit
    }

    override fun migrate(
      driver: SqlDriver,
      oldVersion: Long,
      newVersion: Long,
      vararg callbacks: AfterVersion,
    ): QueryResult.Value<Unit> = QueryResult.Unit
  }
}
