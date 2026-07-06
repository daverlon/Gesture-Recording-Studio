package ai.symly.db

import ai.symly.*
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class DatabaseManager(private val database: GestureDatabase) {
    
    private val queries = database.gestureDatabaseQueries
    
    suspend fun saveGesture(gesture: ai.symly.Gesture) = withContext(Dispatchers.IO) {
        queries.insertGesture(
            id = gesture.id,
            name = gesture.name,
            recordMode = gesture.recordMode.name
        )
    }
    
    suspend fun getAllGestures(): List<ai.symly.Gesture> = withContext(Dispatchers.IO) {
        queries.selectAllGestures().executeAsList().map {
            ai.symly.Gesture(
                id = it.id,
                name = it.name,
                recordMode = ai.symly.RecordMode.valueOf(it.recordMode)
            )
        }
    }
    
    suspend fun getGestureById(id: String): ai.symly.Gesture? = withContext(Dispatchers.IO) {
        queries.selectGestureById(id).executeAsOneOrNull()?.let {
            ai.symly.Gesture(
                id = it.id,
                name = it.name,
                recordMode = ai.symly.RecordMode.valueOf(it.recordMode)
            )
        }
    }
    
    suspend fun deleteGesture(id: String) = withContext(Dispatchers.IO) {
        queries.deleteGesture(id)
    }
    
    suspend fun saveRecording(recording: ai.symly.Recording) = withContext(Dispatchers.IO) {
        println("DEBUG DB saveRecording: id=${recording.id}, gestureId=${recording.gestureId}, sampleSetId=${recording.sampleSetId}, samples=${recording.samples.size}")
        queries.insertRecording(
            id = recording.id,
            gestureId = recording.gestureId,
            timestamp = recording.timestamp,
            durationMs = recording.durationMs,
            prePaddingMs = recording.prePaddingMs,
            postPaddingMs = recording.postPaddingMs,
            sourceCaptureId = recording.sourceCaptureId,
            sampleSetId = recording.sampleSetId,
            offsetMs = recording.offsetMs
        )
        
        recording.samples.forEachIndexed { index, sample ->
            queries.insertImuSample(
                recordingId = recording.id,
                sequence = index.toLong(),
                ax = sample.ax.toDouble(),
                ay = sample.ay.toDouble(),
                az = sample.az.toDouble(),
                gx = sample.gx.toDouble(),
                gy = sample.gy.toDouble(),
                gz = sample.gz.toDouble(),
                roll = sample.roll.toDouble(),
                pitch = sample.pitch.toDouble(),
                yaw = sample.yaw.toDouble()
            )
        }
    }
    
    suspend fun getRecordingsByGesture(gestureId: String): List<ai.symly.Recording> = withContext(Dispatchers.IO) {
        val recordings = queries.selectRecordingsByGesture(gestureId).executeAsList()
        println("DEBUG DB getRecordingsByGesture: gestureId=$gestureId, found ${recordings.size} recordings")
        recordings.mapIndexed { index, rec ->
            val samples = queries.selectSamplesByRecording(rec.id).executeAsList().map {
                ai.symly.ImuSample(
                    ax = it.ax.toFloat(),
                    ay = it.ay.toFloat(),
                    az = it.az.toFloat(),
                    gx = it.gx.toFloat(),
                    gy = it.gy.toFloat(),
                    gz = it.gz.toFloat(),
                    roll = it.roll.toFloat(),
                    pitch = it.pitch.toFloat(),
                    yaw = it.yaw.toFloat()
                )
            }
            if (index < 5 || rec.sampleSetId != null) {
                println("DEBUG DB   recording[$index]: id=${rec.id}, sampleSetId=${rec.sampleSetId}")
            }
            ai.symly.Recording(
                id = rec.id,
                gestureId = rec.gestureId,
                timestamp = rec.timestamp,
                durationMs = rec.durationMs,
                prePaddingMs = rec.prePaddingMs,
                postPaddingMs = rec.postPaddingMs,
                samples = samples,
                sourceCaptureId = rec.sourceCaptureId,
                sampleSetId = rec.sampleSetId,
                offsetMs = rec.offsetMs
            )
        }
    }
    
    suspend fun deleteRecording(id: String) = withContext(Dispatchers.IO) {
        queries.deleteSamplesByRecording(id)
        queries.deleteRecording(id)
    }
    
    suspend fun saveContinuousCapture(capture: ai.symly.ContinuousCapture) = withContext(Dispatchers.IO) {
        queries.insertContinuousCapture(
            id = capture.id,
            gestureId = capture.gestureId,
            timestamp = capture.timestamp,
            durationMs = capture.durationMs
        )
        
        capture.samples.forEachIndexed { index, sample ->
            queries.insertContinuousCaptureSample(
                captureId = capture.id,
                sequence = index.toLong(),
                ax = sample.ax.toDouble(),
                ay = sample.ay.toDouble(),
                az = sample.az.toDouble(),
                gx = sample.gx.toDouble(),
                gy = sample.gy.toDouble(),
                gz = sample.gz.toDouble(),
                roll = sample.roll.toDouble(),
                pitch = sample.pitch.toDouble(),
                yaw = sample.yaw.toDouble()
            )
        }
    }
    
    suspend fun getContinuousCapturesByGesture(gestureId: String): List<ai.symly.ContinuousCapture> = withContext(Dispatchers.IO) {
        val captures = queries.selectContinuousCapturesByGesture(gestureId).executeAsList()
        captures.map { cap ->
            val samples = queries.selectContinuousCaptureSamples(cap.id).executeAsList().map {
                ai.symly.ImuSample(
                    ax = it.ax.toFloat(),
                    ay = it.ay.toFloat(),
                    az = it.az.toFloat(),
                    gx = it.gx.toFloat(),
                    gy = it.gy.toFloat(),
                    gz = it.gz.toFloat(),
                    roll = it.roll.toFloat(),
                    pitch = it.pitch.toFloat(),
                    yaw = it.yaw.toFloat()
                )
            }
            ai.symly.ContinuousCapture(
                id = cap.id,
                gestureId = cap.gestureId,
                timestamp = cap.timestamp,
                durationMs = cap.durationMs,
                samples = samples
            )
        }
    }
    
    suspend fun deleteContinuousCapture(id: String) = withContext(Dispatchers.IO) {
        queries.deleteContinuousCaptureSamples(id)
        queries.deleteContinuousCapture(id)
    }
    
    suspend fun saveSampleSet(sampleSet: ai.symly.SampleSet) = withContext(Dispatchers.IO) {
        println("DEBUG DB: Saving sample set ${sampleSet.id} with ${sampleSet.samples.size} recordings")
        queries.insertSampleSet(
            id = sampleSet.id,
            gestureId = sampleSet.gestureId,
            sourceCaptureId = sampleSet.sourceCaptureId,
            timestamp = sampleSet.timestamp,
            strategy = sampleSet.strategy.name,
            sampleMs = sampleSet.sampleMs,
            prePaddingMs = sampleSet.prePaddingMs,
            postPaddingMs = sampleSet.postPaddingMs,
            stepMs = sampleSet.stepMs,
            randomCount = sampleSet.randomCount?.toLong()
        )
        
        sampleSet.samples.forEachIndexed { index, recording ->
            println("DEBUG DB: Saving recording ${recording.id} (${index + 1}/${sampleSet.samples.size}) with sampleSetId=${recording.sampleSetId}, ${recording.samples.size} samples")
            saveRecording(recording)
        }
        println("DEBUG DB: Finished saving sample set")
    }
    
    suspend fun getSampleSetsByGesture(gestureId: String): List<ai.symly.SampleSet> = withContext(Dispatchers.IO) {
        val sets = queries.selectSampleSetsByGesture(gestureId).executeAsList()
        println("DEBUG DB: Found ${sets.size} sample sets for gesture $gestureId")
        sets.map { set ->
            val allRecordings = getRecordingsByGesture(gestureId)
            println("DEBUG DB: Sample set ${set.id} - total recordings for gesture: ${allRecordings.size}")
            val recordings = allRecordings.filter { it.sampleSetId == set.id }
            println("DEBUG DB: Sample set ${set.id} - filtered recordings with matching sampleSetId: ${recordings.size}")
            recordings.forEach { rec ->
                println("DEBUG DB:   - Recording ${rec.id}: sampleSetId=${rec.sampleSetId}, ${rec.samples.size} samples")
            }
            ai.symly.SampleSet(
                id = set.id,
                gestureId = set.gestureId,
                sourceCaptureId = set.sourceCaptureId,
                timestamp = set.timestamp,
                strategy = ai.symly.SampleStrategy.valueOf(set.strategy),
                sampleMs = set.sampleMs,
                prePaddingMs = set.prePaddingMs,
                postPaddingMs = set.postPaddingMs,
                stepMs = set.stepMs,
                randomCount = set.randomCount?.toInt(),
                samples = recordings
            )
        }
    }
    
    suspend fun deleteSampleSet(id: String) = withContext(Dispatchers.IO) {
        val recordings = queries.selectRecordingsByGesture("").executeAsList()
            .filter { it.sampleSetId == id }
        recordings.forEach { deleteRecording(it.id) }
        queries.deleteSampleSet(id)
    }
}
