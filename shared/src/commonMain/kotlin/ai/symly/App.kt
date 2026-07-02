package ai.symly

import ai.symly.ble.BleClient
import ai.symly.ble.BleDevicePickerDialog
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

// ---------------------------------------------------------------------------
// Style constants
// ---------------------------------------------------------------------------

private val AppFont = FontFamily.SansSerif
private const val SAMPLE_INTERVAL_MS = 20L // 50 Hz
private const val UI_TICK_MS = 16L // ~60 fps progress updates
private const val COUNTDOWN_MS = 3000L

private object Type {
    val label = TextStyle(fontFamily = AppFont, fontSize = 11.sp, color = Color(0xFF6B6B6B))
    val body = TextStyle(fontFamily = AppFont, fontSize = 12.sp, color = Color(0xFF1A1A1A))
    val bodyMedium = TextStyle(fontFamily = AppFont, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1A1A1A))
    val section = TextStyle(fontFamily = AppFont, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF444444))
    val title = TextStyle(fontFamily = AppFont, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A))
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF1A1A1A))
    val monoMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1A1A1A))
    val monoSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF555555))
}

private object Palette {
    val border = Color(0xFFE0E0E0)
    val headerBg = Color(0xFFFAFAFA)
    val selectedBg = Color(0xFFEFF3FB)
    val plotBg = Color(0xFFFBFBFB)
    val padShade = Color(0x14000000)
    val accent = Color(0xFF2B5FD9)
    val danger = Color(0xFFC0392B)
    val muted = Color(0xFF9A9A9A)
    val ok = Color(0xFF2E9E4F)
    val warn = Color(0xFFC98A1E)
}

private val AccelColors = listOf(Color(0xFFE74C3C), Color(0xFFE67E22), Color(0xFFF1C40F)) // ax, ay, az
private val GyroColors = listOf(Color(0xFF27AE60), Color(0xFF16A085), Color(0xFF2ECC71))  // gx, gy, gz
private val EulerColors = listOf(Color(0xFF2980B9), Color(0xFF8E44AD), Color(0xFF34495E)) // roll, pitch, yaw

// ---------------------------------------------------------------------------
// Data models
// ---------------------------------------------------------------------------

data class Gesture(
    val id: String = newId(),
    val name: String,
    val recordMode: RecordMode = RecordMode.TIMED
)

data class ImuSample(
    val ax: Float, val ay: Float, val az: Float,
    val gx: Float, val gy: Float, val gz: Float,
    val roll: Float, val pitch: Float, val yaw: Float
)

private fun formatImuLogLine(sample: ImuSample): String {
    fun v(x: Float): String {
        val rounded = (x * 100).toInt() / 100f
        val text = rounded.toString()
        val dot = text.indexOf('.')
        return if (dot < 0) "$text.00" else text.padEnd(dot + 3, '0').take(dot + 3)
    }
    return "gX:${v(sample.gx)} gY:${v(sample.gy)} gZ:${v(sample.gz)} " +
        "aX:${v(sample.ax)} aY:${v(sample.ay)} aZ:${v(sample.az)} " +
        "r:${v(sample.roll)} p:${v(sample.pitch)} y:${v(sample.yaw)}"
}

data class Recording(
    val id: String = newId(),
    val gestureId: String,
    val timestamp: Long = nowMs(),
    val durationMs: Long,   // total capture span, including leading/trailing padding
    val paddingMs: Long,    // padding applied on each side
    val samples: List<ImuSample>,
    val sourceCaptureId: String? = null,
    val sampleSetId: String? = null,
    val offsetMs: Long? = null
) {
    val sampleCount: Int get() = samples.size
    val coreMs: Long get() = (durationMs - 2 * paddingMs).coerceAtLeast(0)
}

data class SampleSet(
    val id: String = newId(),
    val gestureId: String,
    val sourceCaptureId: String,
    val timestamp: Long = nowMs(),
    val strategy: SampleStrategy,
    val sampleMs: Long,
    val paddingMs: Long,
    val stepMs: Long? = null,
    val randomCount: Int? = null,
    val samples: List<Recording>
)

data class ContinuousCapture(
    val id: String = newId(),
    val gestureId: String,
    val timestamp: Long = nowMs(),
    val durationMs: Long,
    val samples: List<ImuSample>
)

enum class BleStatus { DISCONNECTED, CONNECTING, CONNECTED }
enum class SessionPhase { IDLE, COUNTDOWN, PRE_PAD, CORE, POST_PAD }
enum class RecordMode { TIMED, CONTINUOUS }
enum class SampleStrategy { SLIDING, RANDOM }

fun sliceCapture(
    capture: ContinuousCapture,
    startMs: Long,
    clipMs: Long,
    padMs: Long
): Recording? {
    val windowMs = padMs + clipMs + padMs
    if (startMs + windowMs > capture.durationMs) return null
    val startIdx = (startMs / SAMPLE_INTERVAL_MS).toInt()
    val count = (windowMs / SAMPLE_INTERVAL_MS).toInt().coerceAtLeast(1)
    val endIdx = (startIdx + count).coerceAtMost(capture.samples.size)
    if (endIdx <= startIdx) return null
    return Recording(
        gestureId = capture.gestureId,
        timestamp = capture.timestamp + startMs,
        durationMs = windowMs,
        paddingMs = padMs,
        samples = capture.samples.subList(startIdx, endIdx),
        sourceCaptureId = capture.id,
        offsetMs = startMs
    )
}

fun buildSampleSet(
    capture: ContinuousCapture,
    sampleMs: Long,
    padMs: Long,
    strategy: SampleStrategy,
    stepMs: Long,
    randomCount: Int
): SampleSet? {
    val extracted = extractSamplesFromCapture(capture, sampleMs, padMs, strategy, stepMs, randomCount)
    if (extracted.isEmpty()) return null
    val setId = newId()
    return SampleSet(
        gestureId = capture.gestureId,
        sourceCaptureId = capture.id,
        strategy = strategy,
        sampleMs = sampleMs,
        paddingMs = padMs,
        stepMs = if (strategy == SampleStrategy.SLIDING) stepMs else null,
        randomCount = if (strategy == SampleStrategy.RANDOM) randomCount else null,
        samples = extracted.map { it.copy(sampleSetId = setId) }
    )
}

fun extractSlidingClips(
    capture: ContinuousCapture,
    clipMs: Long,
    padMs: Long,
    stepMs: Long
): List<Recording> {
    if (stepMs <= 0) return emptyList()
    val windowMs = padMs + clipMs + padMs
    val clips = mutableListOf<Recording>()
    var start = 0L
    while (start + windowMs <= capture.durationMs) {
        sliceCapture(capture, start, clipMs, padMs)?.let { clips.add(it) }
        start += stepMs
    }
    return clips
}

fun extractRandomClips(
    capture: ContinuousCapture,
    clipMs: Long,
    padMs: Long,
    count: Int
): List<Recording> {
    if (count <= 0) return emptyList()
    val windowMs = padMs + clipMs + padMs
    val maxStart = capture.durationMs - windowMs
    if (maxStart < 0) return emptyList()
    val rnd = Random(capture.id.hashCode())
    val starts = mutableSetOf<Long>()
    var attempts = 0
    while (starts.size < count && attempts < count * 20) {
        attempts++
        val start = if (maxStart == 0L) 0L else rnd.nextLong(maxStart + 1)
        starts.add(start - (start % SAMPLE_INTERVAL_MS))
    }
    return starts.mapNotNull { sliceCapture(capture, it, clipMs, padMs) }
}

fun extractSamplesFromCapture(
    capture: ContinuousCapture,
    clipMs: Long,
    padMs: Long,
    strategy: SampleStrategy,
    stepMs: Long,
    randomCount: Int
): List<Recording> = when (strategy) {
    SampleStrategy.SLIDING -> extractSlidingClips(capture, clipMs, padMs, stepMs)
    SampleStrategy.RANDOM -> extractRandomClips(capture, clipMs, padMs, randomCount)
}

// TODO: replace with real streamed samples from the NRF52840 (accel/gyro
// raw + Madgwick-fused roll/pitch/yaw). Synthesizes plausible 6-axis + RPY
// data purely so the timing/plotting UI has something real to render.
// fun generateMockImuSamples(count: Int): List<ImuSample> {
//     if (count <= 0) return emptyList()
//     val rnd = Random.Default
//     fun wave(i: Int, freq: Double, amp: Double, phase: Double, offset: Double, noise: Double): Float {
//         val t = i.toDouble() / count
//         return (offset + amp * sin(2 * PI * freq * t + phase) + (rnd.nextDouble() - 0.5) * noise).toFloat()
//     }
//     return (0 until count).map { i ->
//         ImuSample(
//             ax = wave(i, 1.5, 0.6, 0.0, 0.0, 0.05),
//             ay = wave(i, 2.0, 0.4, 1.0, 0.0, 0.05),
//             az = wave(i, 1.0, 0.3, 2.0, 1.0, 0.05),
//             gx = wave(i, 2.5, 80.0, 0.3, 0.0, 5.0),
//             gy = wave(i, 1.8, 60.0, 1.5, 0.0, 5.0),
//             gz = wave(i, 2.2, 50.0, 2.5, 0.0, 5.0),
//             roll = wave(i, 1.2, 20.0, 0.0, 0.0, 1.0),
//             pitch = wave(i, 1.0, 15.0, 1.0, 0.0, 1.0),
//             yaw = wave(i, 0.8, 30.0, 2.0, 0.0, 1.0)
//         )
//     }
// }

// ---------------------------------------------------------------------------
// Root composable
// ---------------------------------------------------------------------------

@Composable
@Preview
fun App(
    databaseManager: ai.symly.db.DatabaseManager? = null,
    onStatusUpdate: ((String) -> Unit)? = null
) {
    var gestures by remember { mutableStateOf(listOf<Gesture>()) }
    var recordings by remember { mutableStateOf(listOf<Recording>()) }
    var sampleSets by remember { mutableStateOf(listOf<SampleSet>()) }
    var continuousCaptures by remember { mutableStateOf(listOf<ContinuousCapture>()) }
    var selectedGestureId by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf(if (databaseManager == null) "No project loaded" else "Ready") }
    var recordedSamples by remember { mutableStateOf(listOf<ImuSample>()) }
    var liveDataLogs by remember { mutableStateOf(listOf<String>()) }
    var liveMessageCount by remember { mutableStateOf(0) }
    var liveDataEnabled by remember { mutableStateOf(false) }
    var sessionStoppedByUser by remember { mutableStateOf(false) }

    var bleStatus by remember { mutableStateOf(BleStatus.DISCONNECTED) }
    var deviceName by remember { mutableStateOf<String?>(null) }
    var showBlePicker by remember { mutableStateOf(false) }
    var bleClient by remember { mutableStateOf<BleClient?>(null) }
    val scope = rememberCoroutineScope()
    
    fun updateStatus(msg: String) {
        status = msg
        onStatusUpdate?.invoke(msg)
    }
    
    LaunchedEffect(databaseManager) {
        if (databaseManager == null) {
            gestures = emptyList()
            recordings = emptyList()
            sampleSets = emptyList()
            continuousCaptures = emptyList()
            selectedGestureId = null
            updateStatus("No project loaded")
            return@LaunchedEffect
        }
        
        try {
            gestures = databaseManager.getAllGestures()
            val allRecordings = mutableListOf<Recording>()
            val allCaptures = mutableListOf<ContinuousCapture>()
            val allSets = mutableListOf<SampleSet>()
            
            gestures.forEach { gesture ->
                allRecordings.addAll(databaseManager.getRecordingsByGesture(gesture.id))
                allCaptures.addAll(databaseManager.getContinuousCapturesByGesture(gesture.id))
                allSets.addAll(databaseManager.getSampleSetsByGesture(gesture.id))
            }
            
            recordings = allRecordings
            continuousCaptures = allCaptures
            sampleSets = allSets
            updateStatus("Project loaded (${gestures.size} gestures)")
        } catch (e: Exception) {
            updateStatus("Error loading project: ${e.message}")
        }
    }

    fun openBlePicker() {
        try {
            if (bleClient == null) {
                bleClient = BleClient()
            }
            showBlePicker = true
        } catch (e: Exception) {
            updateStatus(e.message ?: "Bluetooth unavailable")
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var gesturePendingDelete by remember { mutableStateOf<Gesture?>(null) }

    // Recording session config
    var recordMsInput by remember { mutableStateOf("200") }
    var padMsInput by remember { mutableStateOf("20") }
    var sampleStrategy by remember { mutableStateOf(SampleStrategy.SLIDING) }
    var sampleStepMsInput by remember { mutableStateOf("100") }
    var sampleCountInput by remember { mutableStateOf("10") }

    // Recording session runtime state
    var isSessionActive by remember { mutableStateOf(false) }
    var sessionToken by remember { mutableStateOf(0) }
    var sessionPhase by remember { mutableStateOf(SessionPhase.IDLE) }
    var countdownValue by remember { mutableStateOf(0) }
    var phaseElapsedMs by remember { mutableStateOf(0L) }
    var phaseTargetMs by remember { mutableStateOf(0L) }

    fun handleBleDisconnected(message: String) {
        bleStatus = BleStatus.DISCONNECTED
        deviceName = null
        liveDataEnabled = false
        liveDataLogs = emptyList()
        liveMessageCount = 0
        isSessionActive = false
        sessionPhase = SessionPhase.IDLE
        sessionStoppedByUser = false
        updateStatus(message)
    }

    LaunchedEffect(bleClient) {
        val client = bleClient ?: return@LaunchedEffect
        client.imuSamples.collect { sample ->
            recordedSamples = recordedSamples + sample
            if (recordedSamples.size > 1000) {
                recordedSamples = recordedSamples.takeLast(1000)
            }
            liveDataLogs = (liveDataLogs + formatImuLogLine(sample)).takeLast(LIVE_LOG_BUFFER)
            liveMessageCount++
        }
    }

    LaunchedEffect(sessionToken) {
        if (sessionToken == 0) return@LaunchedEffect
        val gid = selectedGestureId
        if (gid == null) {
            updateStatus("Select a gesture first")
            isSessionActive = false
            return@LaunchedEffect
        }
        val gesture = gestures.find { it.id == gid }
        if (gesture == null) {
            updateStatus("Select a gesture first")
            isSessionActive = false
            return@LaunchedEffect
        }
        val gestureName = gesture.name

        if (gesture.recordMode == RecordMode.CONTINUOUS) {
            sessionPhase = SessionPhase.CORE
            phaseElapsedMs = 0
            phaseTargetMs = 0
            updateStatus("Recording '$gestureName'...")
            val startSampleCount = recordedSamples.size
            while (isSessionActive) {
                delay(UI_TICK_MS)
                phaseElapsedMs += UI_TICK_MS
            }
            if (phaseElapsedMs > 0) {
                val capturedSamples = recordedSamples.drop(startSampleCount)
                if (capturedSamples.isEmpty()) {
                    updateStatus(
                        if (sessionStoppedByUser) "Stopped recording — no sensor data received"
                        else "No sensor data received"
                    )
                } else {
                    val capture = ContinuousCapture(
                        gestureId = gid,
                        durationMs = phaseElapsedMs,
                        samples = capturedSamples
                    )
                    continuousCaptures = continuousCaptures + capture
                    databaseManager?.saveContinuousCapture(capture)
                    updateStatus(
                        if (sessionStoppedByUser) "Stopped recording"
                        else "Saved ${phaseElapsedMs}ms recording for '$gestureName'"
                    )
                }
            } else if (sessionStoppedByUser) {
                updateStatus("Stopped recording")
            }
            sessionPhase = SessionPhase.IDLE
            isSessionActive = false
            sessionStoppedByUser = false
            return@LaunchedEffect
        }

        // TIMED: countdown -> capture (pre+core+post, bar fills seamlessly over pre+core) -> save -> repeat
        suspend fun tickPhase(targetMs: Long, onTick: (Long) -> Unit): Boolean {
            var elapsed = 0L
            while (elapsed < targetMs) {
                if (!isSessionActive) return false
                delay(UI_TICK_MS)
                elapsed = (elapsed + UI_TICK_MS).coerceAtMost(targetMs)
                onTick(elapsed)
            }
            return true
        }

        while (isSessionActive) {
            val coreMs = recordMsInput.toLongOrNull()
            val padMs = padMsInput.toLongOrNull()
            if (coreMs == null || coreMs <= 0 || padMs == null || padMs < 0) {
                updateStatus("Invalid recording configuration")
                isSessionActive = false
                break
            }

            sessionPhase = SessionPhase.COUNTDOWN
            phaseTargetMs = COUNTDOWN_MS
            phaseElapsedMs = 0
            updateStatus("Next recording in 3...")
            if (!tickPhase(COUNTDOWN_MS) { elapsed ->
                phaseElapsedMs = elapsed
                countdownValue = ((COUNTDOWN_MS - elapsed + 999) / 1000).toInt().coerceAtLeast(1)
                updateStatus("Next recording in $countdownValue...")
            }) break

            sessionPhase = SessionPhase.CORE
            phaseTargetMs = coreMs
            phaseElapsedMs = 0
            updateStatus("Recording '$gestureName'...")

            val startSampleCount = recordedSamples.size
            val displayMs = padMs + coreMs
            val totalCaptureMs = padMs + coreMs + padMs
            var captureElapsed = 0L
            while (captureElapsed < totalCaptureMs) {
                if (!isSessionActive) break
                delay(UI_TICK_MS)
                captureElapsed = (captureElapsed + UI_TICK_MS).coerceAtMost(totalCaptureMs)
                phaseElapsedMs = if (displayMs > 0 && captureElapsed <= displayMs) {
                    (captureElapsed * coreMs / displayMs).coerceAtMost(coreMs)
                } else {
                    coreMs
                }
            }
            if (!isSessionActive) break

            val totalMs = padMs + coreMs + padMs
            val capturedSamples = recordedSamples.drop(startSampleCount)
            if (capturedSamples.isEmpty()) {
                updateStatus("No sensor data received")
                break
            }
            val recording = Recording(
                gestureId = gid,
                durationMs = totalMs,
                paddingMs = padMs,
                samples = capturedSamples
            )
            recordings = recordings + recording
            databaseManager?.saveRecording(recording)
            updateStatus("Saved recording for '$gestureName'")
        }
        sessionPhase = SessionPhase.IDLE
        isSessionActive = false
        if (sessionStoppedByUser) {
            updateStatus("Stopped recording")
            sessionStoppedByUser = false
        }
    }

    val selectedGesture = gestures.find { it.id == selectedGestureId }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(modifier = Modifier.weight(1f)) {
            // Left column
            Column(modifier = Modifier.fillMaxHeight().width(220.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Palette.headerBg)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("GESTURES", style = Type.section)
                    CompactIconButton(symbol = "+", onClick = { showAddDialog = true })
                }
                Divider()

                if (gestures.isEmpty()) {
                    Text("No gestures.", style = Type.label, modifier = Modifier.padding(10.dp))
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(gestures, key = { it.id }) { gesture ->
                        val count = recordings.count { it.gestureId == gesture.id } +
                            sampleSets.filter { it.gestureId == gesture.id }.sumOf { it.samples.size }
                        GestureRow(
                            gesture = gesture,
                            recordingCount = count,
                            selected = gesture.id == selectedGestureId,
                            onClick = {
                                selectedGestureId = gesture.id
                                updateStatus("Selected '${gesture.name}'")
                            },
                            onDeleteClick = { gesturePendingDelete = gesture }
                        )
                        Divider()
                    }
                }
            }

            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Palette.border))

            RecordingsPanel(
                gesture = selectedGesture,
                recordings = recordings.filter { it.gestureId == selectedGestureId }
                    .sortedByDescending { it.timestamp },
                sampleSets = sampleSets.filter { it.gestureId == selectedGestureId }
                    .sortedByDescending { it.timestamp },
                continuousCaptures = continuousCaptures.filter { it.gestureId == selectedGestureId }
                    .sortedByDescending { it.timestamp },
                bleConnected = bleStatus == BleStatus.CONNECTED,
                recordMsInput = recordMsInput,
                onRecordMsChange = { recordMsInput = it },
                padMsInput = padMsInput,
                onPadMsChange = { padMsInput = it },
                sampleStrategy = sampleStrategy,
                onSampleStrategyChange = { sampleStrategy = it },
                sampleStepMsInput = sampleStepMsInput,
                onSampleStepMsChange = { sampleStepMsInput = it },
                sampleCountInput = sampleCountInput,
                onSampleCountChange = { sampleCountInput = it },
                isSessionActive = isSessionActive,
                sessionPhase = sessionPhase,
                countdownValue = countdownValue,
                phaseElapsedMs = phaseElapsedMs,
                phaseTargetMs = phaseTargetMs,
                onToggleSession = {
                    if (isSessionActive) {
                        sessionStoppedByUser = true
                        isSessionActive = false
                        sessionPhase = SessionPhase.IDLE
                        updateStatus("Stopped recording")
                    } else if (bleStatus != BleStatus.CONNECTED) {
                        updateStatus("Connect device to record")
                    } else {
                        sessionStoppedByUser = false
                        isSessionActive = true
                        sessionToken++
                    }
                },
                onSampleCapture = { capture ->
                    val sampleMs = recordMsInput.toLongOrNull()
                    val padMs = padMsInput.toLongOrNull()
                    val stepMs = sampleStepMsInput.toLongOrNull()
                    val randomCount = sampleCountInput.toIntOrNull()
                    when {
                        sampleMs == null || sampleMs <= 0 || padMs == null || padMs < 0 ->
                            updateStatus("Invalid sample configuration")
                        sampleStrategy == SampleStrategy.SLIDING && (stepMs == null || stepMs <= 0) ->
                            updateStatus("Invalid step configuration")
                        sampleStrategy == SampleStrategy.RANDOM && (randomCount == null || randomCount <= 0) ->
                            updateStatus("Invalid sample count")
                        else -> {
                            val set = buildSampleSet(
                                capture = capture,
                                sampleMs = sampleMs,
                                padMs = padMs,
                                strategy = sampleStrategy,
                                stepMs = stepMs ?: 0,
                                randomCount = randomCount ?: 0
                            )
                            if (set == null) {
                                updateStatus("No samples fit in recording")
                            } else {
                                sampleSets = sampleSets + set
                                scope.launch {
                                    databaseManager?.saveSampleSet(set)
                                }
                                updateStatus("Created sample set (${set.samples.size} samples)")
                            }
                        }
                    }
                },
                onDeleteRecording = { recording ->
                    recordings = recordings - recording
                    scope.launch {
                        databaseManager?.deleteRecording(recording.id)
                    }
                    updateStatus("Deleted recording")
                },
                onDeleteSampleSet = { set ->
                    sampleSets = sampleSets - set
                    scope.launch {
                        databaseManager?.deleteSampleSet(set.id)
                    }
                    updateStatus("Deleted sample set")
                },
                onDeleteSampleFromSet = { set, sample ->
                    val updated = set.samples.filter { it.id != sample.id }
                    sampleSets = if (updated.isEmpty()) {
                        sampleSets - set
                    } else {
                        sampleSets.map { if (it.id == set.id) it.copy(samples = updated) else it }
                    }
                    scope.launch {
                        databaseManager?.deleteRecording(sample.id)
                        if (updated.isEmpty()) {
                            databaseManager?.deleteSampleSet(set.id)
                        }
                    }
                    updateStatus("Deleted sample")
                },
                onDeleteCapture = { capture ->
                    continuousCaptures = continuousCaptures - capture
                    sampleSets = sampleSets.filter { it.sourceCaptureId != capture.id }
                    scope.launch {
                        databaseManager?.deleteContinuousCapture(capture.id)
                    }
                    updateStatus("Deleted recording")
                }
            )
        }

        Divider()

        BottomStatusArea(
            status = status,
            bleStatus = bleStatus,
            deviceName = deviceName,
            liveDataEnabled = liveDataEnabled,
            liveMessageCount = liveMessageCount,
            liveSamples = recordedSamples,
            liveLogs = liveDataLogs,
            onLiveDataToggle = { liveDataEnabled = it },
            onConnectClick = {
                when (bleStatus) {
                    BleStatus.DISCONNECTED -> openBlePicker()
                    BleStatus.CONNECTED -> scope.launch {
                        try {
                            bleClient?.disconnect()
                        } catch (_: Exception) {
                        }
                        handleBleDisconnected("Disconnected")
                    }
                    BleStatus.CONNECTING -> { /* no-op */ }
                }
            }
        )
    }

        if (showBlePicker) {
            bleClient?.let { client ->
            BleDevicePickerDialog(
                bleClient = client,
                onDismiss = {
                    showBlePicker = false
                    client.stopScan()
                },
                onDeviceSelected = { device ->
                    showBlePicker = false
                    client.stopScan()
                    scope.launch {
                        bleStatus = BleStatus.CONNECTING
                        updateStatus("Connecting to ${device.name}...")
                        try {
                            val name = client.connect(device.id)
                            deviceName = name
                            bleStatus = BleStatus.CONNECTED
                            client.startConnectionMonitor(scope) {
                                handleBleDisconnected("Connection lost")
                            }
                            updateStatus("Connected to $name")
                        } catch (e: Exception) {
                            handleBleDisconnected("Connect failed: ${e.message ?: "unknown error"}")
                        }
                    }
                }
            )
            }
        }

        if (showAddDialog) {
            AddGestureDialog(
                existingNames = gestures.map { it.name },
                onDismiss = { showAddDialog = false },
                onConfirm = { name, mode ->
                    val gesture = Gesture(name = name, recordMode = mode)
                    gestures = gestures + gesture
                    selectedGestureId = gesture.id
                    scope.launch {
                        databaseManager?.saveGesture(gesture)
                    }
                    updateStatus("Added gesture '$name'")
                    showAddDialog = false
                }
            )
        }

        gesturePendingDelete?.let { gesture ->
            val recordingCount = recordings.count { it.gestureId == gesture.id }
            val captureCount = continuousCaptures.count { it.gestureId == gesture.id }
            val setCount = sampleSets.count { it.gestureId == gesture.id }
            AppDialog(
                title = "Delete '${gesture.name}'?",
                onDismiss = { gesturePendingDelete = null },
                confirmText = "Delete",
                onConfirm = {
                    recordings = recordings.filter { it.gestureId != gesture.id }
                    sampleSets = sampleSets.filter { it.gestureId != gesture.id }
                    continuousCaptures = continuousCaptures.filter { it.gestureId != gesture.id }
                    gestures = gestures.filter { it.id != gesture.id }
                    if (selectedGestureId == gesture.id) selectedGestureId = null
                    scope.launch {
                        databaseManager?.deleteGesture(gesture.id)
                    }
                    updateStatus("Deleted gesture '${gesture.name}'")
                    gesturePendingDelete = null
                },
                confirmEnabled = true,
                confirmAccent = Palette.danger
            ) {
                val parts = buildList {
                    if (gesture.recordMode == RecordMode.CONTINUOUS) {
                        if (captureCount > 0) add("$captureCount recording(s)")
                        if (setCount > 0) add("$setCount sample set(s)")
                    } else if (recordingCount > 0) {
                        add("$recordingCount recording(s)")
                    }
                }
                Text(
                    if (parts.isEmpty()) "Deletes this gesture." else "Deletes ${parts.joinToString(", ")}.",
                    style = Type.body
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared minimal controls
// ---------------------------------------------------------------------------

@Composable
fun Modifier.compactClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    clickable(
        enabled = enabled,
        indication = null,
        interactionSource = remember { MutableInteractionSource() },
        onClick = onClick
    )

@Composable
fun CompactButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
    accent: Color = Palette.accent
) {
    val bg = if (filled) (if (enabled) accent else Palette.muted) else Color.Transparent
    val fg = if (filled) Color.White else if (enabled) accent else Palette.muted
    Box(
        modifier = modifier
            .height(24.dp)
            .border(width = if (filled) 0.dp else 1.dp, color = if (enabled) accent else Palette.border, shape = RoundedCornerShape(3.dp))
            .clip(RoundedCornerShape(3.dp))
            .background(bg)
            .then(if (enabled) Modifier.compactClickable { onClick() } else Modifier)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = TextStyle(fontFamily = AppFont, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = fg))
    }
}

@Composable
fun CompactIconButton(symbol: String, onClick: () -> Unit, tint: Color = Color(0xFF777777)) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(2.dp))
            .compactClickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, style = TextStyle(fontFamily = AppFont, fontSize = 12.sp, color = tint))
    }
}

/** Small fixed-width numeric input with an inline label, no Material padding. */
@Composable
fun CompactNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    width: androidx.compose.ui.unit.Dp = 52.dp
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Text(label, style = Type.label)
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .width(width)
                .height(24.dp)
                .border(width = 1.dp, color = Palette.border, shape = RoundedCornerShape(3.dp))
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = { new -> if (new.length <= 6 && new.all { it.isDigit() }) onValueChange(new) },
                enabled = enabled,
                singleLine = true,
                textStyle = Type.mono,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Small fixed-width text input with an inline label. */
@Composable
fun CompactTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false
) {
    Column(modifier = modifier) {
        Text(label, style = Type.label)
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .border(
                    width = 1.dp,
                    color = if (isError) Palette.danger else Palette.border,
                    shape = RoundedCornerShape(3.dp)
                )
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = Type.body,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun CompactCheckRow(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .compactClickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .border(1.dp, if (checked) Palette.accent else Palette.border, RoundedCornerShape(2.dp))
                .background(if (checked) Palette.accent else Color.White, RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Text("\u2713", style = TextStyle(fontFamily = AppFont, fontSize = 8.sp, color = Color.White))
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, style = Type.body)
    }
}

@Composable
fun CompactMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalDensity.current
    if (expanded) {
        Popup(
            alignment = Alignment.TopEnd,
            offset = IntOffset(0, with(density) { 26.dp.roundToPx() }),
            onDismissRequest = onDismiss
        ) {
            Column(
                modifier = modifier
                    .width(132.dp)
                    .border(1.dp, Palette.border, RoundedCornerShape(3.dp))
                    .background(Color.White, RoundedCornerShape(3.dp))
                    .padding(vertical = 2.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    confirmAccent: Color = Palette.accent,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrimInteraction = remember { MutableInteractionSource() }
    val panelInteraction = remember { MutableInteractionSource() }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color(0x66000000))
                .clickable(
                    indication = null,
                    interactionSource = scrimInteraction,
                    onClick = {}
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .width(280.dp)
                .border(1.dp, Palette.border, RoundedCornerShape(4.dp))
                .background(Color.White, RoundedCornerShape(4.dp))
                .clickable(
                    indication = null,
                    interactionSource = panelInteraction,
                    onClick = {}
                )
                .padding(12.dp)
        ) {
            Text(title, style = Type.title)
            Spacer(modifier = Modifier.height(12.dp))
            content()
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactButton(text = "Cancel", onClick = onDismiss)
                Spacer(modifier = Modifier.width(8.dp))
                CompactButton(
                    text = confirmText,
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    filled = true,
                    accent = confirmAccent
                )
            }
        }
    }
}

@Composable
fun Divider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Palette.border))
}

// ---------------------------------------------------------------------------
// Status bar + live data strip
// ---------------------------------------------------------------------------

private const val LIVE_PLOT_SAMPLES = 150
private const val LIVE_LOG_BUFFER = 5000
private val LiveDataStripMinHeight = 56.dp
private val LiveDataStripMaxHeight = 480.dp
private val LiveDataStripDefaultHeight = 120.dp

@Composable
fun LiveDataResizeHandle(
    onDragDeltaY: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(Palette.headerBg)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDragDeltaY(dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Palette.muted)
        )
    }
}

@Composable
fun LiveDataLogPanel(
    logs: List<String>,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val logText = logs.joinToString("\n")

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.scrollToItem(logs.lastIndex)
        }
    }

    Box(
        modifier = modifier.background(Palette.plotBg)
    ) {
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Waiting for messages...", style = Type.label)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .pointerInput(logText) {
                        detectTapGestures {
                            clipboard.setText(AnnotatedString(logText))
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(logs, key = { index, _ -> index }) { _, line ->
                    Text(
                        text = line,
                        style = Type.monoSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun BottomStatusArea(
    status: String,
    bleStatus: BleStatus,
    deviceName: String?,
    liveDataEnabled: Boolean,
    liveMessageCount: Int,
    liveSamples: List<ImuSample>,
    liveLogs: List<String>,
    onLiveDataToggle: (Boolean) -> Unit,
    onConnectClick: () -> Unit
) {
    var stripHeight by remember { mutableStateOf(LiveDataStripDefaultHeight) }
    val density = LocalDensity.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.headerBg)
    ) {
        if (liveDataEnabled && bleStatus == BleStatus.CONNECTED) {
            LiveDataResizeHandle(
                onDragDeltaY = { dragY ->
                    stripHeight = (stripHeight - with(density) { dragY.toDp() })
                        .coerceIn(LiveDataStripMinHeight, LiveDataStripMaxHeight)
                }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(stripHeight)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    if (liveSamples.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Palette.plotBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Waiting for sensor data...", style = Type.label)
                        }
                    } else {
                        ImuPlot(
                            samples = liveSamples.takeLast(LIVE_PLOT_SAMPLES),
                            paddingMs = 0,
                            compact = true,
                            fillHeight = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(Palette.border)
                )
                LiveDataLogPanel(
                    logs = liveLogs,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
            Divider()
        }
        StatusBar(
            status = status,
            bleStatus = bleStatus,
            deviceName = deviceName,
            liveDataEnabled = liveDataEnabled,
            liveMessageCount = liveMessageCount,
            onLiveDataToggle = onLiveDataToggle,
            onConnectClick = onConnectClick
        )
    }
}

@Composable
fun StatusBar(
    status: String,
    bleStatus: BleStatus,
    deviceName: String?,
    liveDataEnabled: Boolean,
    liveMessageCount: Int,
    onLiveDataToggle: (Boolean) -> Unit,
    onConnectClick: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = status,
            style = Type.label,
            modifier = Modifier
                .weight(1f)
                .clickable { clipboard.setText(AnnotatedString(status)) }
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (bleStatus == BleStatus.CONNECTED) {
                Text(
                    text = liveMessageCount.toString(),
                    style = Type.monoMedium,
                    modifier = Modifier.widthIn(min = 36.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            CompactButton(
                text = "Live Data",
                onClick = { onLiveDataToggle(!liveDataEnabled) },
                enabled = bleStatus == BleStatus.CONNECTED,
                filled = liveDataEnabled
            )
            Spacer(modifier = Modifier.width(10.dp))
            val (dotColor, label) = when (bleStatus) {
                BleStatus.DISCONNECTED -> Palette.muted to "Disconnected"
                BleStatus.CONNECTING -> Palette.warn to "Connecting..."
                BleStatus.CONNECTED -> Palette.ok to (deviceName ?: "Connected")
            }
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dotColor))
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, style = Type.label)
            Spacer(modifier = Modifier.width(10.dp))
            CompactButton(
                text = if (bleStatus == BleStatus.CONNECTED) "Disconnect" else "Connect",
                onClick = onConnectClick,
                enabled = bleStatus != BleStatus.CONNECTING,
                filled = true
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Left column row
// ---------------------------------------------------------------------------

@Composable
fun GestureRow(
    gesture: Gesture,
    recordingCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Palette.selectedBg else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(gesture.name, style = if (selected) Type.bodyMedium else Type.body, modifier = Modifier.weight(1f))
        Text(
            if (gesture.recordMode == RecordMode.TIMED) "T" else "C",
            style = Type.monoSmall,
            modifier = Modifier.padding(end = 4.dp)
        )
        Text("$recordingCount", style = Type.label, modifier = Modifier.padding(end = 6.dp))
        CompactIconButton(symbol = "\u2715", onClick = onDeleteClick)
    }
}

// ---------------------------------------------------------------------------
// Right column — recordings workspace
// ---------------------------------------------------------------------------

@Composable
fun RecordingsPanel(
    gesture: Gesture?,
    recordings: List<Recording>,
    sampleSets: List<SampleSet>,
    continuousCaptures: List<ContinuousCapture>,
    bleConnected: Boolean,
    recordMsInput: String,
    onRecordMsChange: (String) -> Unit,
    padMsInput: String,
    onPadMsChange: (String) -> Unit,
    sampleStrategy: SampleStrategy,
    onSampleStrategyChange: (SampleStrategy) -> Unit,
    sampleStepMsInput: String,
    onSampleStepMsChange: (String) -> Unit,
    sampleCountInput: String,
    onSampleCountChange: (String) -> Unit,
    isSessionActive: Boolean,
    sessionPhase: SessionPhase,
    countdownValue: Int,
    phaseElapsedMs: Long,
    phaseTargetMs: Long,
    onToggleSession: () -> Unit,
    onSampleCapture: (ContinuousCapture) -> Unit,
    onDeleteRecording: (Recording) -> Unit,
    onDeleteSampleSet: (SampleSet) -> Unit,
    onDeleteSampleFromSet: (SampleSet, Recording) -> Unit,
    onDeleteCapture: (ContinuousCapture) -> Unit
) {
    var showPlots by remember { mutableStateOf(false) }
    var autoScroll by remember { mutableStateOf(true) }
    var viewMenuExpanded by remember { mutableStateOf(false) }
    var selectedCaptureId by remember(gesture?.id) { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    var prevListSize by remember { mutableStateOf(0) }
    val listSize = if (gesture?.recordMode == RecordMode.CONTINUOUS) sampleSets.size else recordings.size
    val selectedCapture = continuousCaptures.find { it.id == selectedCaptureId }

    LaunchedEffect(continuousCaptures.map { it.id }) {
        selectedCaptureId = when {
            continuousCaptures.isEmpty() -> null
            selectedCaptureId == null -> continuousCaptures.first().id
            continuousCaptures.none { it.id == selectedCaptureId } -> continuousCaptures.first().id
            else -> selectedCaptureId
        }
    }

    LaunchedEffect(listSize) {
        if (autoScroll && listSize > prevListSize) {
            listState.scrollToItem(0)
        }
        prevListSize = listSize
    }

    Column(modifier = Modifier.fillMaxHeight().fillMaxWidth()) {
        if (gesture == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No gesture selected.", style = Type.label)
            }
            return@Column
        }

        // Section header: title, session controls, plot toggle — single row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Palette.headerBg)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(gesture.name.uppercase(), style = Type.section)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (gesture.recordMode == RecordMode.TIMED) "TIMED" else "CONTINUOUS",
                    style = Type.label.copy(color = Palette.muted)
                )
                if (gesture.recordMode == RecordMode.TIMED) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("(${recordings.size})", style = Type.label)
                } else {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("(${continuousCaptures.size})", style = Type.label)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (gesture.recordMode == RecordMode.TIMED) {
                    CompactNumberField("REC ms", recordMsInput, onRecordMsChange, enabled = !isSessionActive)
                    Spacer(modifier = Modifier.width(8.dp))
                    CompactNumberField("PAD ms", padMsInput, onPadMsChange, enabled = !isSessionActive)
                    Spacer(modifier = Modifier.width(10.dp))
                }

                if (isSessionActive) {
                    Text(
                        sessionStatusLabel(gesture.recordMode, sessionPhase, countdownValue, phaseElapsedMs, phaseTargetMs),
                        style = Type.mono
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                CompactButton(
                    text = when {
                        isSessionActive -> "Stop"
                        gesture.recordMode == RecordMode.CONTINUOUS -> "Record"
                        else -> "Start"
                    },
                    onClick = onToggleSession,
                    enabled = isSessionActive || bleConnected,
                    filled = isSessionActive,
                    accent = if (isSessionActive) Palette.danger else Palette.accent
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box {
                    CompactButton(
                        text = "View \u25BE",
                        onClick = { viewMenuExpanded = true },
                        filled = showPlots || autoScroll
                    )
                    CompactMenu(
                        expanded = viewMenuExpanded,
                        onDismiss = { viewMenuExpanded = false }
                    ) {
                        CompactCheckRow("Show plots", showPlots) { showPlots = !showPlots }
                        CompactCheckRow("Auto scroll", autoScroll) { autoScroll = !autoScroll }
                    }
                }
            }
        }
        Divider()

        if (isSessionActive && gesture.recordMode == RecordMode.TIMED &&
            (sessionPhase == SessionPhase.COUNTDOWN || sessionPhase == SessionPhase.CORE)
        ) {
            SessionProgressBar(
                phase = sessionPhase,
                phaseElapsedMs = phaseElapsedMs,
                phaseTargetMs = phaseTargetMs,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
            )
            Divider()
        }

        if (gesture.recordMode == RecordMode.CONTINUOUS) {
            Column(modifier = Modifier.fillMaxWidth().background(Palette.plotBg)) {
                if (continuousCaptures.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp)) {
                        Text("#", style = Type.label, modifier = Modifier.width(28.dp))
                        Text("TIME", style = Type.label, modifier = Modifier.width(90.dp))
                        Text("LENGTH", style = Type.label, modifier = Modifier.width(80.dp))
                        Text("PTS", style = Type.label, modifier = Modifier.width(70.dp))
                        Text("", modifier = Modifier.weight(1f))
                    }
                    Divider()
                    continuousCaptures.forEachIndexed { index, capture ->
                        CaptureRow(
                            number = continuousCaptures.size - index,
                            capture = capture,
                            selected = capture.id == selectedCaptureId,
                            onClick = { selectedCaptureId = capture.id },
                            onDelete = { onDeleteCapture(capture) }
                        )
                        Divider()
                    }
                }
                SamplingConfigRow(
                    sampleMsInput = recordMsInput,
                    onSampleMsChange = onRecordMsChange,
                    padMsInput = padMsInput,
                    onPadMsChange = onPadMsChange,
                    sampleStrategy = sampleStrategy,
                    onSampleStrategyChange = onSampleStrategyChange,
                    sampleStepMsInput = sampleStepMsInput,
                    onSampleStepMsChange = onSampleStepMsChange,
                    sampleCountInput = sampleCountInput,
                    onSampleCountChange = onSampleCountChange,
                    onSample = { selectedCapture?.let(onSampleCapture) },
                    sampleEnabled = selectedCapture != null && !isSessionActive
                )
            }
            Divider()
            SectionLabel("SAMPLE SETS", sampleSets.size)
            Divider()
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp)) {
                Text("", modifier = Modifier.width(16.dp))
                Text("TIME", style = Type.label, modifier = Modifier.width(84.dp))
                Text("N", style = Type.label, modifier = Modifier.width(28.dp))
                Text("STRATEGY", style = Type.label, modifier = Modifier.width(88.dp))
                Text("SAMPLE", style = Type.label, modifier = Modifier.width(52.dp))
                Text("PAD", style = Type.label, modifier = Modifier.width(44.dp))
                Text("", modifier = Modifier.weight(1f))
            }
            Divider()
        }

        if (gesture.recordMode == RecordMode.TIMED) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp)) {
                Text("#", style = Type.label, modifier = Modifier.width(28.dp))
                Text("TIME", style = Type.label, modifier = Modifier.width(90.dp))
                Text("DURATION", style = Type.label, modifier = Modifier.width(80.dp))
                Text("PAD", style = Type.label, modifier = Modifier.width(60.dp))
                Text("PTS", style = Type.label, modifier = Modifier.width(70.dp))
                Text("", modifier = Modifier.weight(1f))
            }
            Divider()
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            if (gesture.recordMode == RecordMode.CONTINUOUS) {
                items(sampleSets, key = { it.id }) { set ->
                    SampleSetRow(
                        set = set,
                        showPlots = showPlots,
                        onDeleteSet = { onDeleteSampleSet(set) },
                        onDeleteSample = { sample -> onDeleteSampleFromSet(set, sample) }
                    )
                    Divider()
                }
            } else {
                items(recordings, key = { it.id }) { recording ->
                    val index = recordings.size - recordings.indexOf(recording)
                    RecordingRow(index = index, recording = recording, onDelete = { onDeleteRecording(recording) })
                    if (showPlots) {
                        ImuPlot(samples = recording.samples, paddingMs = recording.paddingMs, modifier = Modifier.fillMaxWidth())
                    }
                    Divider()
                }
            }
        }
    }
}

@Composable
fun SectionLabel(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = Type.section)
        Text("$count", style = Type.label)
    }
}

@Composable
fun SamplingConfigRow(
    sampleMsInput: String,
    onSampleMsChange: (String) -> Unit,
    padMsInput: String,
    onPadMsChange: (String) -> Unit,
    sampleStrategy: SampleStrategy,
    onSampleStrategyChange: (SampleStrategy) -> Unit,
    sampleStepMsInput: String,
    onSampleStepMsChange: (String) -> Unit,
    sampleCountInput: String,
    onSampleCountChange: (String) -> Unit,
    onSample: () -> Unit,
    sampleEnabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.headerBg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompactNumberField("SAMPLE ms", sampleMsInput, onSampleMsChange)
        Spacer(modifier = Modifier.width(8.dp))
        CompactNumberField("PAD ms", padMsInput, onPadMsChange)
        Spacer(modifier = Modifier.width(8.dp))
        SampleStrategySelector(strategy = sampleStrategy, onStrategyChange = onSampleStrategyChange)
        Spacer(modifier = Modifier.width(8.dp))
        if (sampleStrategy == SampleStrategy.SLIDING) {
            CompactNumberField("STEP ms", sampleStepMsInput, onSampleStepMsChange)
        } else {
            CompactNumberField("N", sampleCountInput, onSampleCountChange, width = 40.dp)
        }
        Spacer(modifier = Modifier.weight(1f))
        CompactButton(text = "Sample", onClick = onSample, enabled = sampleEnabled, filled = sampleEnabled)
    }
}

@Composable
fun SampleStrategySelector(
    strategy: SampleStrategy,
    onStrategyChange: (SampleStrategy) -> Unit,
    enabled: Boolean = true
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        CompactButton(
            text = "Sliding",
            onClick = { onStrategyChange(SampleStrategy.SLIDING) },
            enabled = enabled,
            filled = strategy == SampleStrategy.SLIDING
        )
        CompactButton(
            text = "Random",
            onClick = { onStrategyChange(SampleStrategy.RANDOM) },
            enabled = enabled,
            filled = strategy == SampleStrategy.RANDOM
        )
    }
}

@Composable
fun CaptureRow(
    number: Int,
    capture: ContinuousCapture,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Palette.selectedBg else Color.Transparent)
            .compactClickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$number", style = Type.mono, modifier = Modifier.width(28.dp))
        Text(formatTimestamp(capture.timestamp), style = Type.mono, modifier = Modifier.width(90.dp))
        Text("${capture.durationMs}ms", style = Type.mono, modifier = Modifier.width(80.dp))
        Text("${capture.samples.size} pts", style = Type.mono, modifier = Modifier.width(70.dp))
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactIconButton(symbol = "\u2715", onClick = onDelete)
        }
    }
}

@Composable
fun SampleSetRow(
    set: SampleSet,
    showPlots: Boolean,
    onDeleteSet: () -> Unit,
    onDeleteSample: (Recording) -> Unit
) {
    var expanded by remember(set.id) { mutableStateOf(false) }
    val strategyLabel = when (set.strategy) {
        SampleStrategy.SLIDING -> "Sliding ${set.stepMs}ms"
        SampleStrategy.RANDOM -> "Random n=${set.randomCount}"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (expanded) Palette.selectedBg else Color.Transparent)
                .compactClickable { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (expanded) "\u25BE" else "\u25B8", style = Type.mono, modifier = Modifier.width(12.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(formatTimestamp(set.timestamp), style = Type.mono, modifier = Modifier.width(84.dp))
            Text("${set.samples.size}", style = Type.monoMedium, modifier = Modifier.width(28.dp))
            Text(strategyLabel, style = Type.monoSmall, modifier = Modifier.width(88.dp))
            Text("${set.sampleMs}ms", style = Type.monoSmall, modifier = Modifier.width(52.dp))
            Text("\u00B1${set.paddingMs}", style = Type.monoSmall, modifier = Modifier.width(44.dp))
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactIconButton(symbol = "\u2715", onClick = onDeleteSet)
            }
        }
        if (expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Palette.plotBg)
                    .padding(start = 26.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)
            ) {
                Text("#", style = Type.label, modifier = Modifier.width(24.dp))
                Text("TIME", style = Type.label, modifier = Modifier.width(84.dp))
                Text("DURATION", style = Type.label, modifier = Modifier.width(72.dp))
                Text("PAD", style = Type.label, modifier = Modifier.width(48.dp))
                Text("PTS", style = Type.label, modifier = Modifier.width(48.dp))
            }
            set.samples.forEachIndexed { index, sample ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Palette.plotBg)
                        .padding(start = 26.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${set.samples.size - index}", style = Type.mono, modifier = Modifier.width(24.dp))
                    Text(formatTimestamp(sample.timestamp), style = Type.mono, modifier = Modifier.width(84.dp))
                    Text("${sample.durationMs}ms", style = Type.mono, modifier = Modifier.width(72.dp))
                    Text("\u00B1${sample.paddingMs}ms", style = Type.mono, modifier = Modifier.width(48.dp))
                    Text("${sample.sampleCount}", style = Type.mono, modifier = Modifier.width(48.dp))
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                        CompactIconButton(symbol = "\u2715", onClick = { onDeleteSample(sample) })
                    }
                }
                if (showPlots) {
                    ImuPlot(
                        samples = sample.samples,
                        paddingMs = sample.paddingMs,
                        modifier = Modifier.fillMaxWidth().padding(start = 26.dp)
                    )
                }
                Divider()
            }
        }
    }
}

@Composable
fun RecordModeSelector(
    mode: RecordMode,
    onModeChange: (RecordMode) -> Unit,
    enabled: Boolean = true
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        CompactButton(
            text = "Timed",
            onClick = { onModeChange(RecordMode.TIMED) },
            enabled = enabled,
            filled = mode == RecordMode.TIMED
        )
        CompactButton(
            text = "Continuous",
            onClick = { onModeChange(RecordMode.CONTINUOUS) },
            enabled = enabled,
            filled = mode == RecordMode.CONTINUOUS
        )
    }
}

@Composable
fun SessionProgressBar(
    phase: SessionPhase,
    phaseElapsedMs: Long,
    phaseTargetMs: Long,
    modifier: Modifier = Modifier
) {
    val target = phaseTargetMs.coerceAtLeast(1)
    val progress = when (phase) {
        SessionPhase.CORE -> phaseElapsedMs.toFloat() / phaseTargetMs
        else -> phaseElapsedMs.toFloat() / target
    }.coerceIn(0f, 1f)
    val (label, trackColor) = when (phase) {
        SessionPhase.COUNTDOWN -> {
            val secs = ((phaseTargetMs - phaseElapsedMs) + 999) / 1000
            "Next recording in $secs\u2026" to Palette.warn
        }
        SessionPhase.CORE -> "Recording\u2026" to Palette.accent
        else -> return
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = Type.label)
            if (phase == SessionPhase.CORE) {
                Text("${phaseElapsedMs}/${phaseTargetMs}ms", style = Type.monoSmall)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Palette.border)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .clip(RoundedCornerShape(3.dp))
                    .background(trackColor)
            )
        }
    }
}

private fun sessionStatusLabel(
    mode: RecordMode,
    phase: SessionPhase,
    countdown: Int,
    elapsed: Long,
    target: Long
): String = when {
    mode == RecordMode.CONTINUOUS && phase == SessionPhase.CORE -> "${elapsed}ms"
    phase == SessionPhase.COUNTDOWN -> "in $countdown"
    phase == SessionPhase.CORE && target > 0 -> "$elapsed/${target}ms"
    else -> ""
}

@Composable
fun RecordingRow(
    index: Int,
    recording: Recording,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$index", style = Type.mono, modifier = Modifier.width(28.dp))
        Text(formatTimestamp(recording.timestamp), style = Type.mono, modifier = Modifier.width(90.dp))
        Text("${recording.durationMs}ms", style = Type.mono, modifier = Modifier.width(80.dp))
        Text("\u00B1${recording.paddingMs}ms", style = Type.mono, modifier = Modifier.width(60.dp))
        Text("${recording.sampleCount}", style = Type.mono, modifier = Modifier.width(70.dp))
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
            CompactIconButton(symbol = "\u2715", onClick = onDelete)
        }
    }
}

// ---------------------------------------------------------------------------
// IMU plot — inline, full width, fixed low height, padding regions shaded
// ---------------------------------------------------------------------------

@Composable
fun ImuPlot(
    samples: List<ImuSample>,
    paddingMs: Long,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    fillHeight: Boolean = false
) {
    if (samples.isEmpty()) return
    val totalMs = (samples.size * SAMPLE_INTERVAL_MS).toFloat()
    val preFrac = (paddingMs / totalMs).coerceIn(0f, 1f)
    val postFrac = 1f - preFrac
    val plotHeight = if (compact) 48.dp else 72.dp

    Column(
        modifier = modifier
            .background(Palette.plotBg)
            .padding(horizontal = 10.dp, vertical = if (compact) 4.dp else 6.dp)
    ) {
        val canvasModifier = if (fillHeight) {
            Modifier.fillMaxWidth().weight(1f)
        } else {
            Modifier.fillMaxWidth().height(plotHeight)
        }
        Canvas(modifier = canvasModifier) {
            val w = size.width
            val h = size.height
            val preX = w * preFrac
            val postX = w * postFrac

            if (paddingMs > 0) {
                drawRect(color = Palette.padShade, topLeft = Offset(0f, 0f), size = Size(preX, h))
                drawRect(color = Palette.padShade, topLeft = Offset(postX, 0f), size = Size(w - postX, h))
                val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
                drawLine(Color(0xFF999999), Offset(preX, 0f), Offset(preX, h), strokeWidth = 1f, pathEffect = dash)
                drawLine(Color(0xFF999999), Offset(postX, 0f), Offset(postX, h), strokeWidth = 1f, pathEffect = dash)
            }

            fun DrawScope.drawGroup(series: List<List<Float>>, colors: List<Color>) {
                val all = series.flatten()
                val minV = all.min()
                val maxV = all.max()
                val range = (maxV - minV).takeIf { it > 0.0001f } ?: 1f
                series.forEachIndexed { ci, values ->
                    val path = Path()
                    values.forEachIndexed { i, v ->
                        val x = w * i / (values.size - 1).coerceAtLeast(1)
                        val norm = (v - minV) / range
                        val y = h - norm * h
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, color = colors[ci], style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round))
                }
            }
            drawGroup(listOf(samples.map { it.ax }, samples.map { it.ay }, samples.map { it.az }), AccelColors)
            drawGroup(listOf(samples.map { it.gx }, samples.map { it.gy }, samples.map { it.gz }), GyroColors)
            drawGroup(listOf(samples.map { it.roll }, samples.map { it.pitch }, samples.map { it.yaw }), EulerColors)
        }
        if (!compact) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LegendItem("ax", AccelColors[0]); LegendItem("ay", AccelColors[1]); LegendItem("az", AccelColors[2])
                LegendItem("gx", GyroColors[0]); LegendItem("gy", GyroColors[1]); LegendItem("gz", GyroColors[2])
                LegendItem("roll", EulerColors[0]); LegendItem("pitch", EulerColors[1]); LegendItem("yaw", EulerColors[2])
            }
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(1.dp)).background(color))
        Spacer(modifier = Modifier.width(3.dp))
        Text(label, style = Type.monoSmall)
    }
}

// ---------------------------------------------------------------------------
// Add gesture dialog
// ---------------------------------------------------------------------------

@Composable
fun AddGestureDialog(
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, RecordMode) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var recordMode by remember { mutableStateOf(RecordMode.TIMED) }
    val trimmed = name.trim()
    val isDuplicate = existingNames.any { it.equals(trimmed, ignoreCase = true) }
    val isValid = trimmed.isNotEmpty() && !isDuplicate

    AppDialog(
        title = "New gesture",
        onDismiss = onDismiss,
        confirmText = "Add",
        onConfirm = { onConfirm(trimmed, recordMode) },
        confirmEnabled = isValid
    ) {
        CompactTextField(
            label = "Name",
            value = name,
            onValueChange = { name = it },
            isError = isDuplicate
        )
        if (isDuplicate) {
            Text("Name exists", style = Type.label.copy(color = Palette.danger), modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text("Mode", style = Type.label)
        Spacer(modifier = Modifier.height(6.dp))
        RecordModeSelector(mode = recordMode, onModeChange = { recordMode = it })
    }
}