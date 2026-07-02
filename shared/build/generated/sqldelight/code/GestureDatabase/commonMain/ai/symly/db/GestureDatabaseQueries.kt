package ai.symly.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import kotlin.Any
import kotlin.Double
import kotlin.Long
import kotlin.String

public class GestureDatabaseQueries(
  driver: SqlDriver,
) : TransacterImpl(driver) {
  public fun <T : Any> selectAllGestures(mapper: (
    id: String,
    name: String,
    recordMode: String,
  ) -> T): Query<T> = Query(337_189_657, arrayOf("Gesture"), driver, "GestureDatabase.sq",
      "selectAllGestures",
      "SELECT Gesture.id, Gesture.name, Gesture.recordMode FROM Gesture ORDER BY name") { cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!
    )
  }

  public fun selectAllGestures(): Query<Gesture> = selectAllGestures { id, name, recordMode ->
    Gesture(
      id,
      name,
      recordMode
    )
  }

  public fun <T : Any> selectGestureById(id: String, mapper: (
    id: String,
    name: String,
    recordMode: String,
  ) -> T): Query<T> = SelectGestureByIdQuery(id) { cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!
    )
  }

  public fun selectGestureById(id: String): Query<Gesture> = selectGestureById(id) { id_, name,
      recordMode ->
    Gesture(
      id_,
      name,
      recordMode
    )
  }

  public fun <T : Any> selectRecordingsByGesture(gestureId: String, mapper: (
    id: String,
    gestureId: String,
    timestamp: Long,
    durationMs: Long,
    paddingMs: Long,
    sourceCaptureId: String?,
    sampleSetId: String?,
    offsetMs: Long?,
  ) -> T): Query<T> = SelectRecordingsByGestureQuery(gestureId) { cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!,
      cursor.getLong(2)!!,
      cursor.getLong(3)!!,
      cursor.getLong(4)!!,
      cursor.getString(5),
      cursor.getString(6),
      cursor.getLong(7)
    )
  }

  public fun selectRecordingsByGesture(gestureId: String): Query<Recording> =
      selectRecordingsByGesture(gestureId) { id, gestureId_, timestamp, durationMs, paddingMs,
      sourceCaptureId, sampleSetId, offsetMs ->
    Recording(
      id,
      gestureId_,
      timestamp,
      durationMs,
      paddingMs,
      sourceCaptureId,
      sampleSetId,
      offsetMs
    )
  }

  public fun <T : Any> selectRecordingById(id: String, mapper: (
    id: String,
    gestureId: String,
    timestamp: Long,
    durationMs: Long,
    paddingMs: Long,
    sourceCaptureId: String?,
    sampleSetId: String?,
    offsetMs: Long?,
  ) -> T): Query<T> = SelectRecordingByIdQuery(id) { cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!,
      cursor.getLong(2)!!,
      cursor.getLong(3)!!,
      cursor.getLong(4)!!,
      cursor.getString(5),
      cursor.getString(6),
      cursor.getLong(7)
    )
  }

  public fun selectRecordingById(id: String): Query<Recording> = selectRecordingById(id) { id_,
      gestureId, timestamp, durationMs, paddingMs, sourceCaptureId, sampleSetId, offsetMs ->
    Recording(
      id_,
      gestureId,
      timestamp,
      durationMs,
      paddingMs,
      sourceCaptureId,
      sampleSetId,
      offsetMs
    )
  }

  public fun <T : Any> selectSamplesByRecording(recordingId: String, mapper: (
    id: Long,
    recordingId: String,
    sequence: Long,
    ax: Double,
    ay: Double,
    az: Double,
    gx: Double,
    gy: Double,
    gz: Double,
    roll: Double,
    pitch: Double,
    yaw: Double,
  ) -> T): Query<T> = SelectSamplesByRecordingQuery(recordingId) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getLong(2)!!,
      cursor.getDouble(3)!!,
      cursor.getDouble(4)!!,
      cursor.getDouble(5)!!,
      cursor.getDouble(6)!!,
      cursor.getDouble(7)!!,
      cursor.getDouble(8)!!,
      cursor.getDouble(9)!!,
      cursor.getDouble(10)!!,
      cursor.getDouble(11)!!
    )
  }

  public fun selectSamplesByRecording(recordingId: String): Query<ImuSample> =
      selectSamplesByRecording(recordingId) { id, recordingId_, sequence, ax, ay, az, gx, gy, gz,
      roll, pitch, yaw ->
    ImuSample(
      id,
      recordingId_,
      sequence,
      ax,
      ay,
      az,
      gx,
      gy,
      gz,
      roll,
      pitch,
      yaw
    )
  }

  public fun <T : Any> selectContinuousCapturesByGesture(gestureId: String, mapper: (
    id: String,
    gestureId: String,
    timestamp: Long,
    durationMs: Long,
  ) -> T): Query<T> = SelectContinuousCapturesByGestureQuery(gestureId) { cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!,
      cursor.getLong(2)!!,
      cursor.getLong(3)!!
    )
  }

  public fun selectContinuousCapturesByGesture(gestureId: String): Query<ContinuousCapture> =
      selectContinuousCapturesByGesture(gestureId) { id, gestureId_, timestamp, durationMs ->
    ContinuousCapture(
      id,
      gestureId_,
      timestamp,
      durationMs
    )
  }

  public fun <T : Any> selectContinuousCaptureById(id: String, mapper: (
    id: String,
    gestureId: String,
    timestamp: Long,
    durationMs: Long,
  ) -> T): Query<T> = SelectContinuousCaptureByIdQuery(id) { cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!,
      cursor.getLong(2)!!,
      cursor.getLong(3)!!
    )
  }

  public fun selectContinuousCaptureById(id: String): Query<ContinuousCapture> =
      selectContinuousCaptureById(id) { id_, gestureId, timestamp, durationMs ->
    ContinuousCapture(
      id_,
      gestureId,
      timestamp,
      durationMs
    )
  }

  public fun <T : Any> selectContinuousCaptureSamples(captureId: String, mapper: (
    id: Long,
    captureId: String,
    sequence: Long,
    ax: Double,
    ay: Double,
    az: Double,
    gx: Double,
    gy: Double,
    gz: Double,
    roll: Double,
    pitch: Double,
    yaw: Double,
  ) -> T): Query<T> = SelectContinuousCaptureSamplesQuery(captureId) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getLong(2)!!,
      cursor.getDouble(3)!!,
      cursor.getDouble(4)!!,
      cursor.getDouble(5)!!,
      cursor.getDouble(6)!!,
      cursor.getDouble(7)!!,
      cursor.getDouble(8)!!,
      cursor.getDouble(9)!!,
      cursor.getDouble(10)!!,
      cursor.getDouble(11)!!
    )
  }

  public fun selectContinuousCaptureSamples(captureId: String): Query<ContinuousCaptureSample> =
      selectContinuousCaptureSamples(captureId) { id, captureId_, sequence, ax, ay, az, gx, gy, gz,
      roll, pitch, yaw ->
    ContinuousCaptureSample(
      id,
      captureId_,
      sequence,
      ax,
      ay,
      az,
      gx,
      gy,
      gz,
      roll,
      pitch,
      yaw
    )
  }

  public fun <T : Any> selectSampleSetsByGesture(gestureId: String, mapper: (
    id: String,
    gestureId: String,
    sourceCaptureId: String,
    timestamp: Long,
    strategy: String,
    sampleMs: Long,
    paddingMs: Long,
    stepMs: Long?,
    randomCount: Long?,
  ) -> T): Query<T> = SelectSampleSetsByGestureQuery(gestureId) { cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getLong(3)!!,
      cursor.getString(4)!!,
      cursor.getLong(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7),
      cursor.getLong(8)
    )
  }

  public fun selectSampleSetsByGesture(gestureId: String): Query<SampleSet> =
      selectSampleSetsByGesture(gestureId) { id, gestureId_, sourceCaptureId, timestamp, strategy,
      sampleMs, paddingMs, stepMs, randomCount ->
    SampleSet(
      id,
      gestureId_,
      sourceCaptureId,
      timestamp,
      strategy,
      sampleMs,
      paddingMs,
      stepMs,
      randomCount
    )
  }

  public fun <T : Any> selectSampleSetById(id: String, mapper: (
    id: String,
    gestureId: String,
    sourceCaptureId: String,
    timestamp: Long,
    strategy: String,
    sampleMs: Long,
    paddingMs: Long,
    stepMs: Long?,
    randomCount: Long?,
  ) -> T): Query<T> = SelectSampleSetByIdQuery(id) { cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getLong(3)!!,
      cursor.getString(4)!!,
      cursor.getLong(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7),
      cursor.getLong(8)
    )
  }

  public fun selectSampleSetById(id: String): Query<SampleSet> = selectSampleSetById(id) { id_,
      gestureId, sourceCaptureId, timestamp, strategy, sampleMs, paddingMs, stepMs, randomCount ->
    SampleSet(
      id_,
      gestureId,
      sourceCaptureId,
      timestamp,
      strategy,
      sampleMs,
      paddingMs,
      stepMs,
      randomCount
    )
  }

  public fun insertGesture(
    id: String,
    name: String,
    recordMode: String,
  ) {
    driver.execute(309_373_434, """
        |INSERT OR REPLACE INTO Gesture(id, name, recordMode)
        |VALUES (?, ?, ?)
        """.trimMargin(), 3) {
          bindString(0, id)
          bindString(1, name)
          bindString(2, recordMode)
        }
    notifyQueries(309_373_434) { emit ->
      emit("Gesture")
    }
  }

  public fun deleteGesture(id: String) {
    driver.execute(616_108_744, """DELETE FROM Gesture WHERE id = ?""", 1) {
          bindString(0, id)
        }
    notifyQueries(616_108_744) { emit ->
      emit("ContinuousCapture")
      emit("Gesture")
      emit("Recording")
      emit("SampleSet")
    }
  }

  public fun insertRecording(
    id: String,
    gestureId: String,
    timestamp: Long,
    durationMs: Long,
    paddingMs: Long,
    sourceCaptureId: String?,
    sampleSetId: String?,
    offsetMs: Long?,
  ) {
    driver.execute(1_086_480_994, """
        |INSERT OR REPLACE INTO Recording(id, gestureId, timestamp, durationMs, paddingMs, sourceCaptureId, sampleSetId, offsetMs)
        |VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 8) {
          bindString(0, id)
          bindString(1, gestureId)
          bindLong(2, timestamp)
          bindLong(3, durationMs)
          bindLong(4, paddingMs)
          bindString(5, sourceCaptureId)
          bindString(6, sampleSetId)
          bindLong(7, offsetMs)
        }
    notifyQueries(1_086_480_994) { emit ->
      emit("Recording")
    }
  }

  public fun deleteRecording(id: String) {
    driver.execute(-493_629_520, """DELETE FROM Recording WHERE id = ?""", 1) {
          bindString(0, id)
        }
    notifyQueries(-493_629_520) { emit ->
      emit("ImuSample")
      emit("Recording")
    }
  }

  public fun insertImuSample(
    recordingId: String,
    sequence: Long,
    ax: Double,
    ay: Double,
    az: Double,
    gx: Double,
    gy: Double,
    gz: Double,
    roll: Double,
    pitch: Double,
    yaw: Double,
  ) {
    driver.execute(-790_561_460, """
        |INSERT INTO ImuSample(recordingId, sequence, ax, ay, az, gx, gy, gz, roll, pitch, yaw)
        |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 11) {
          bindString(0, recordingId)
          bindLong(1, sequence)
          bindDouble(2, ax)
          bindDouble(3, ay)
          bindDouble(4, az)
          bindDouble(5, gx)
          bindDouble(6, gy)
          bindDouble(7, gz)
          bindDouble(8, roll)
          bindDouble(9, pitch)
          bindDouble(10, yaw)
        }
    notifyQueries(-790_561_460) { emit ->
      emit("ImuSample")
    }
  }

  public fun deleteSamplesByRecording(recordingId: String) {
    driver.execute(-1_946_029_902, """DELETE FROM ImuSample WHERE recordingId = ?""", 1) {
          bindString(0, recordingId)
        }
    notifyQueries(-1_946_029_902) { emit ->
      emit("ImuSample")
    }
  }

  public fun insertContinuousCapture(
    id: String,
    gestureId: String,
    timestamp: Long,
    durationMs: Long,
  ) {
    driver.execute(-765_939_544, """
        |INSERT OR REPLACE INTO ContinuousCapture(id, gestureId, timestamp, durationMs)
        |VALUES (?, ?, ?, ?)
        """.trimMargin(), 4) {
          bindString(0, id)
          bindString(1, gestureId)
          bindLong(2, timestamp)
          bindLong(3, durationMs)
        }
    notifyQueries(-765_939_544) { emit ->
      emit("ContinuousCapture")
    }
  }

  public fun deleteContinuousCapture(id: String) {
    driver.execute(1_665_593_334, """DELETE FROM ContinuousCapture WHERE id = ?""", 1) {
          bindString(0, id)
        }
    notifyQueries(1_665_593_334) { emit ->
      emit("ContinuousCapture")
      emit("ContinuousCaptureSample")
      emit("SampleSet")
    }
  }

  public fun insertContinuousCaptureSample(
    captureId: String,
    sequence: Long,
    ax: Double,
    ay: Double,
    az: Double,
    gx: Double,
    gy: Double,
    gz: Double,
    roll: Double,
    pitch: Double,
    yaw: Double,
  ) {
    driver.execute(1_129_837_682, """
        |INSERT INTO ContinuousCaptureSample(captureId, sequence, ax, ay, az, gx, gy, gz, roll, pitch, yaw)
        |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 11) {
          bindString(0, captureId)
          bindLong(1, sequence)
          bindDouble(2, ax)
          bindDouble(3, ay)
          bindDouble(4, az)
          bindDouble(5, gx)
          bindDouble(6, gy)
          bindDouble(7, gz)
          bindDouble(8, roll)
          bindDouble(9, pitch)
          bindDouble(10, yaw)
        }
    notifyQueries(1_129_837_682) { emit ->
      emit("ContinuousCaptureSample")
    }
  }

  public fun deleteContinuousCaptureSamples(captureId: String) {
    driver.execute(1_604_265_779, """DELETE FROM ContinuousCaptureSample WHERE captureId = ?""", 1)
        {
          bindString(0, captureId)
        }
    notifyQueries(1_604_265_779) { emit ->
      emit("ContinuousCaptureSample")
    }
  }

  public fun insertSampleSet(
    id: String,
    gestureId: String,
    sourceCaptureId: String,
    timestamp: Long,
    strategy: String,
    sampleMs: Long,
    paddingMs: Long,
    stepMs: Long?,
    randomCount: Long?,
  ) {
    driver.execute(1_205_918_409, """
        |INSERT OR REPLACE INTO SampleSet(id, gestureId, sourceCaptureId, timestamp, strategy, sampleMs, paddingMs, stepMs, randomCount)
        |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 9) {
          bindString(0, id)
          bindString(1, gestureId)
          bindString(2, sourceCaptureId)
          bindLong(3, timestamp)
          bindString(4, strategy)
          bindLong(5, sampleMs)
          bindLong(6, paddingMs)
          bindLong(7, stepMs)
          bindLong(8, randomCount)
        }
    notifyQueries(1_205_918_409) { emit ->
      emit("SampleSet")
    }
  }

  public fun deleteSampleSet(id: String) {
    driver.execute(-374_192_105, """DELETE FROM SampleSet WHERE id = ?""", 1) {
          bindString(0, id)
        }
    notifyQueries(-374_192_105) { emit ->
      emit("SampleSet")
    }
  }

  private inner class SelectGestureByIdQuery<out T : Any>(
    public val id: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("Gesture", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("Gesture", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(2_141_946_569,
        """SELECT Gesture.id, Gesture.name, Gesture.recordMode FROM Gesture WHERE id = ?""", mapper,
        1) {
      bindString(0, id)
    }

    override fun toString(): String = "GestureDatabase.sq:selectGestureById"
  }

  private inner class SelectRecordingsByGestureQuery<out T : Any>(
    public val gestureId: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("Recording", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("Recording", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(982_471_294,
        """SELECT Recording.id, Recording.gestureId, Recording.timestamp, Recording.durationMs, Recording.paddingMs, Recording.sourceCaptureId, Recording.sampleSetId, Recording.offsetMs FROM Recording WHERE gestureId = ? ORDER BY timestamp DESC""",
        mapper, 1) {
      bindString(0, gestureId)
    }

    override fun toString(): String = "GestureDatabase.sq:selectRecordingsByGesture"
  }

  private inner class SelectRecordingByIdQuery<out T : Any>(
    public val id: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("Recording", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("Recording", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(558_679_025,
        """SELECT Recording.id, Recording.gestureId, Recording.timestamp, Recording.durationMs, Recording.paddingMs, Recording.sourceCaptureId, Recording.sampleSetId, Recording.offsetMs FROM Recording WHERE id = ?""",
        mapper, 1) {
      bindString(0, id)
    }

    override fun toString(): String = "GestureDatabase.sq:selectRecordingById"
  }

  private inner class SelectSamplesByRecordingQuery<out T : Any>(
    public val recordingId: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("ImuSample", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("ImuSample", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-78_262_845,
        """SELECT ImuSample.id, ImuSample.recordingId, ImuSample.sequence, ImuSample.ax, ImuSample.ay, ImuSample.az, ImuSample.gx, ImuSample.gy, ImuSample.gz, ImuSample.roll, ImuSample.pitch, ImuSample.yaw FROM ImuSample WHERE recordingId = ? ORDER BY sequence""",
        mapper, 1) {
      bindString(0, recordingId)
    }

    override fun toString(): String = "GestureDatabase.sq:selectSamplesByRecording"
  }

  private inner class SelectContinuousCapturesByGestureQuery<out T : Any>(
    public val gestureId: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("ContinuousCapture", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("ContinuousCapture", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(1_188_090_948,
        """SELECT ContinuousCapture.id, ContinuousCapture.gestureId, ContinuousCapture.timestamp, ContinuousCapture.durationMs FROM ContinuousCapture WHERE gestureId = ? ORDER BY timestamp DESC""",
        mapper, 1) {
      bindString(0, gestureId)
    }

    override fun toString(): String = "GestureDatabase.sq:selectContinuousCapturesByGesture"
  }

  private inner class SelectContinuousCaptureByIdQuery<out T : Any>(
    public val id: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("ContinuousCapture", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("ContinuousCapture", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-701_722_057,
        """SELECT ContinuousCapture.id, ContinuousCapture.gestureId, ContinuousCapture.timestamp, ContinuousCapture.durationMs FROM ContinuousCapture WHERE id = ?""",
        mapper, 1) {
      bindString(0, id)
    }

    override fun toString(): String = "GestureDatabase.sq:selectContinuousCaptureById"
  }

  private inner class SelectContinuousCaptureSamplesQuery<out T : Any>(
    public val captureId: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("ContinuousCaptureSample", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("ContinuousCaptureSample", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(153_301_892,
        """SELECT ContinuousCaptureSample.id, ContinuousCaptureSample.captureId, ContinuousCaptureSample.sequence, ContinuousCaptureSample.ax, ContinuousCaptureSample.ay, ContinuousCaptureSample.az, ContinuousCaptureSample.gx, ContinuousCaptureSample.gy, ContinuousCaptureSample.gz, ContinuousCaptureSample.roll, ContinuousCaptureSample.pitch, ContinuousCaptureSample.yaw FROM ContinuousCaptureSample WHERE captureId = ? ORDER BY sequence""",
        mapper, 1) {
      bindString(0, captureId)
    }

    override fun toString(): String = "GestureDatabase.sq:selectContinuousCaptureSamples"
  }

  private inner class SelectSampleSetsByGestureQuery<out T : Any>(
    public val gestureId: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("SampleSet", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("SampleSet", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-1_879_329_243,
        """SELECT SampleSet.id, SampleSet.gestureId, SampleSet.sourceCaptureId, SampleSet.timestamp, SampleSet.strategy, SampleSet.sampleMs, SampleSet.paddingMs, SampleSet.stepMs, SampleSet.randomCount FROM SampleSet WHERE gestureId = ? ORDER BY timestamp DESC""",
        mapper, 1) {
      bindString(0, gestureId)
    }

    override fun toString(): String = "GestureDatabase.sq:selectSampleSetsByGesture"
  }

  private inner class SelectSampleSetByIdQuery<out T : Any>(
    public val id: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("SampleSet", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("SampleSet", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(169_521_368,
        """SELECT SampleSet.id, SampleSet.gestureId, SampleSet.sourceCaptureId, SampleSet.timestamp, SampleSet.strategy, SampleSet.sampleMs, SampleSet.paddingMs, SampleSet.stepMs, SampleSet.randomCount FROM SampleSet WHERE id = ?""",
        mapper, 1) {
      bindString(0, id)
    }

    override fun toString(): String = "GestureDatabase.sq:selectSampleSetById"
  }
}
