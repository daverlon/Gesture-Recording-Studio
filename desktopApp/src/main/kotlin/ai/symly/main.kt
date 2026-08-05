package ai.symly

import ai.symly.db.DatabaseManager
import ai.symly.db.DriverFactory
import ai.symly.db.GestureDatabase
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.*
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.util.prefs.Preferences

private val prefs = Preferences.userNodeForPackage(object {}.javaClass)
private const val PREF_SETTINGS = "settings"
private const val PREF_LAST_PROJECT = "lastProject"

private fun loadSettings(): AppSettings {
    val json = prefs.get(PREF_SETTINGS, null) ?: return AppSettings()
    return try {
        Json.decodeFromString(json)
    } catch (e: Exception) {
        AppSettings()
    }
}

private fun saveSettings(settings: AppSettings) {
    prefs.put(PREF_SETTINGS, Json.encodeToString(settings))
}

private fun saveLastProject(path: String) {
    prefs.put(PREF_LAST_PROJECT, path)
}

private fun loadLastProject(): String? {
    return prefs.get(PREF_LAST_PROJECT, null)
}

fun main() = application {
    val scope = rememberCoroutineScope()
    
    var currentProjectPath by remember { mutableStateOf<String?>(null) }
    var database by remember { mutableStateOf<DatabaseManager?>(null) }
    var statusMessage by remember { mutableStateOf("No project loaded") }
    var settings by remember { mutableStateOf(loadSettings()) }
    var showSettings by remember { mutableStateOf(false) }
    
    // Auto-open last project on startup
    LaunchedEffect(Unit) {
        if (settings.autoOpenLastProject) {
            loadLastProject()?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    try {
                        val driver = DriverFactory.createDriver(file.absolutePath)
                        val db = GestureDatabase(driver)
                        database = DatabaseManager(db)
                        currentProjectPath = file.absolutePath
                        statusMessage = "Opened ${file.name}"
                    } catch (e: Exception) {
                        statusMessage = "Failed to auto-open last project: ${e.message}"
                    }
                }
            }
        }
    }
    
    fun createNewProject(path: String) {
        scope.launch {
            try {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
                val driver = DriverFactory.createDriver(path)
                val db = GestureDatabase(driver)
                database = DatabaseManager(db)
                currentProjectPath = path
                saveLastProject(path)
                statusMessage = "Created new project: ${file.name}"
                println("DEBUG: Created new project at $path")
            } catch (e: Exception) {
                statusMessage = "Error creating project: ${e.message}"
                e.printStackTrace()
            }
        }
    }
    
    fun openProject(path: String) {
        scope.launch {
            try {
                val file = File(path)
                if (!file.exists()) {
                    statusMessage = "File not found: ${file.name}"
                    return@launch
                }
                val driver = DriverFactory.createDriver(path)
                database = DatabaseManager(GestureDatabase(driver))
                currentProjectPath = path
                saveLastProject(path)
                statusMessage = "Opened project: ${file.name}"
            } catch (e: Exception) {
                statusMessage = "Error opening project: ${e.message}"
            }
        }
    }
    
    fun saveProjectAs(newPath: String) {
        scope.launch {
            try {
                val currentPath = currentProjectPath
                if (currentPath == null) {
                    statusMessage = "No project to save"
                    return@launch
                }
                
                val sourceFile = File(currentPath)
                val destFile = File(newPath)
                
                if (destFile.exists()) {
                    destFile.delete()
                }
                
                // Copy the database file
                sourceFile.copyTo(destFile, overwrite = true)
                
                // Reopen at the new location
                val driver = DriverFactory.createDriver(newPath)
                database = DatabaseManager(GestureDatabase(driver))
                currentProjectPath = newPath
                saveLastProject(newPath)
                statusMessage = "Saved project as: ${destFile.name}"
            } catch (e: Exception) {
                statusMessage = "Error saving project: ${e.message}"
            }
        }
    }
    
    fun showNewProjectDialog() {
        val dialog = FileDialog(null as Frame?, "New Project", FileDialog.SAVE)
        dialog.file = "project.gestures"
        dialog.isVisible = true
        val filename = dialog.file
        val directory = dialog.directory
        if (filename != null && directory != null) {
            val path = File(directory, filename).absolutePath
            createNewProject(path)
        }
    }
    
    fun showOpenDialog() {
        val dialog = FileDialog(null as Frame?, "Open Project", FileDialog.LOAD)
        dialog.file = "*.gestures"
        dialog.isVisible = true
        val filename = dialog.file
        val directory = dialog.directory
        if (filename != null && directory != null) {
            val path = File(directory, filename).absolutePath
            openProject(path)
        }
    }
    
    fun showSaveAsDialog() {
        val dialog = FileDialog(null as Frame?, "Save Project As", FileDialog.SAVE)
        dialog.file = currentProjectPath?.let { File(it).name } ?: "project.gestures"
        dialog.isVisible = true
        val filename = dialog.file
        val directory = dialog.directory
        if (filename != null && directory != null) {
            val path = File(directory, filename).absolutePath
            saveProjectAs(path)
        }
    }
    
    // Helper functions for export
    fun buildRecordingCsv(gestureName: String, mode: RecordMode, recording: Recording): String {
        return buildString {
            appendLine("# Gesture: $gestureName, Mode: $mode, Duration: ${recording.durationMs}ms, PrePadding: ${recording.prePaddingMs}ms, PostPadding: ${recording.postPaddingMs}ms")
            appendLine("# Recorded: ${java.time.Instant.ofEpochMilli(recording.timestamp)}, Samples: ${recording.samples.size}")
            appendLine("timestamp_ms,ax,ay,az,gx,gy,gz,roll,pitch,yaw")
            recording.samples.forEachIndexed { index, sample ->
                val timestamp = index * 10 // 100Hz = 10ms intervals
                appendLine("$timestamp,${sample.ax},${sample.ay},${sample.az},${sample.gx},${sample.gy},${sample.gz},${sample.roll},${sample.pitch},${sample.yaw}")
            }
        }
    }
    
    fun buildCaptureCsv(gestureName: String, capture: ContinuousCapture): String {
        return buildString {
            appendLine("# Gesture: $gestureName, Mode: CONTINUOUS, Duration: ${capture.durationMs}ms")
            appendLine("# Recorded: ${java.time.Instant.ofEpochMilli(capture.timestamp)}, Samples: ${capture.samples.size}")
            appendLine("timestamp_ms,ax,ay,az,gx,gy,gz,roll,pitch,yaw")
            capture.samples.forEachIndexed { index, sample ->
                val timestamp = index * 10
                appendLine("$timestamp,${sample.ax},${sample.ay},${sample.az},${sample.gx},${sample.gy},${sample.gz},${sample.roll},${sample.pitch},${sample.yaw}")
            }
        }
    }
    
    fun buildSampleSetInfo(sampleSet: SampleSet): String {
        return buildString {
            appendLine("Sample Set Information")
            appendLine("Strategy: ${sampleSet.strategy}")
            appendLine("Sample Duration: ${sampleSet.sampleMs}ms")
            when (sampleSet.strategy) {
                SampleStrategy.SLIDING -> appendLine("Step: ${sampleSet.stepMs}ms")
                SampleStrategy.RANDOM -> appendLine("Count: ${sampleSet.randomCount}")
            }
            appendLine("Total Samples: ${sampleSet.samples.size}")
            appendLine("Created: ${java.time.Instant.ofEpochMilli(sampleSet.timestamp)}")
        }
    }
    
    fun addToDatasetAll(list: MutableList<String>, gestureName: String, sampleId: String, samples: List<ImuSample>) {
        samples.forEachIndexed { index, sample ->
            val timestamp = index * 10
            list.add("$gestureName,$sampleId,$timestamp,${sample.ax},${sample.ay},${sample.az},${sample.gx},${sample.gy},${sample.gz},${sample.roll},${sample.pitch},${sample.yaw}")
        }
    }
    
    suspend fun exportProject(db: DatabaseManager, exportPath: String, projectName: String) {
        try {
            val exportDir = File(exportPath)
            if (exportDir.exists()) {
                exportDir.deleteRecursively()
            }
            exportDir.mkdirs()
            
            // Get all data
            val gestures = db.getAllGestures()
            val allSamplesForDataset = mutableListOf<String>()
            
            // Create README
            File(exportDir, "README.txt").writeText("""
                Dataset Export: $projectName
                Exported: ${java.time.LocalDateTime.now()}
                
                Structure:
                - dataset_all.csv: All samples from all gestures (for ML training)
                - [GestureName]_[Mode]/: One folder per gesture
                  - raw_recordings/ or raw_captures/: Individual recordings
                  - sample_sets/: Processed sample sets
                
                CSV Format:
                - First rows starting with # are metadata comments
                - Data columns: timestamp_ms,ax,ay,az,gx,gy,gz,roll,pitch,yaw
                - Timestamp is relative to sample start (0ms = first sample)
                
                Sample Rate: 100Hz (10ms intervals)
            """.trimIndent())
            
            // Export each gesture
            for (gesture in gestures) {
                val gestureName = gesture.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                val gestureDir = File(exportDir, "${gestureName}_${gesture.recordMode}")
                gestureDir.mkdirs()
                
                if (gesture.recordMode == RecordMode.TIMED) {
                    // Export timed recordings
                    val recordings = db.getRecordingsByGesture(gesture.id)
                    val rawDir = File(gestureDir, "raw_recordings")
                    rawDir.mkdirs()
                    
                    recordings.forEachIndexed { index, recording ->
                        val recFile = File(rawDir, "rec_${(index + 1).toString().padStart(3, '0')}.csv")
                        val csv = buildRecordingCsv(gesture.name, gesture.recordMode, recording)
                        recFile.writeText(csv)
                        
                        // Add to dataset_all
                        addToDatasetAll(allSamplesForDataset, gesture.name, "rec_${index + 1}", recording.samples)
                    }
                } else {
                    // Export continuous captures
                    val captures = db.getContinuousCapturesByGesture(gesture.id)
                    val rawDir = File(gestureDir, "raw_captures")
                    rawDir.mkdirs()
                    
                    captures.forEachIndexed { index, capture ->
                        val capFile = File(rawDir, "cap_${(index + 1).toString().padStart(3, '0')}.csv")
                        val csv = buildCaptureCsv(gesture.name, capture)
                        capFile.writeText(csv)
                        
                        // Add to dataset_all
                        addToDatasetAll(allSamplesForDataset, gesture.name, "cap_${index + 1}", capture.samples)
                    }
                }
                
                // Export sample sets
                val sampleSets = db.getSampleSetsByGesture(gesture.id)
                println("DEBUG: Gesture ${gesture.name} has ${sampleSets.size} sample sets")
                if (sampleSets.isNotEmpty()) {
                    val setsDir = File(gestureDir, "sample_sets")
                    setsDir.mkdirs()
                    
                    sampleSets.forEachIndexed { setIndex, sampleSet ->
                        val setName = "${sampleSet.strategy.name.lowercase()}_${(setIndex + 1).toString().padStart(3, '0')}"
                        val setDir = File(setsDir, setName)
                        setDir.mkdirs()
                        println("DEBUG: Sample set ${setName} has ${sampleSet.samples.size} samples")
                        
                        // Write info file
                        File(setDir, "info.txt").writeText(buildSampleSetInfo(sampleSet))
                        
                        // Write each sample
                        sampleSet.samples.forEachIndexed { sampleIndex, recording ->
                            val sampleFile = File(setDir, "sample_${(sampleIndex + 1).toString().padStart(3, '0')}.csv")
                            val csv = buildRecordingCsv(gesture.name, gesture.recordMode, recording)
                            sampleFile.writeText(csv)
                            
                            // Add to dataset_all
                            addToDatasetAll(allSamplesForDataset, gesture.name, "${setName}_sample_${sampleIndex + 1}", recording.samples)
                        }
                    }
                }
            }
            
            // Write dataset_all.csv
            val datasetAllFile = File(exportDir, "dataset_all.csv")
            val datasetCsv = buildString {
                appendLine("gesture,sample_id,timestamp_ms,ax,ay,az,gx,gy,gz,roll,pitch,yaw")
                allSamplesForDataset.forEach { appendLine(it) }
            }
            datasetAllFile.writeText(datasetCsv)
            
            statusMessage = "Exported ${gestures.size} gestures to ${exportDir.name}"
        } catch (e: Exception) {
            statusMessage = "Export failed: ${e.message}"
            e.printStackTrace()
        }
    }
    
    fun showExportDialog() {
        scope.launch {
            try {
                val db = database
                val projectPath = currentProjectPath
                if (db == null || projectPath == null) {
                    statusMessage = "No project to export"
                    return@launch
                }
                
                // Use file dialog to pick directory
                val dialog = FileDialog(null as Frame?, "Choose Export Location", FileDialog.SAVE)
                val projectName = File(projectPath).nameWithoutExtension
                dialog.file = "${projectName}_Export"
                dialog.isVisible = true
                val filename = dialog.file
                val directory = dialog.directory
                
                if (filename != null && directory != null) {
                    val exportPath = File(directory, filename).absolutePath
                    statusMessage = "Exporting..."
                    exportProject(db, exportPath, projectName)
                }
            } catch (e: Exception) {
                statusMessage = "Export error: ${e.message}"
                e.printStackTrace()
            }
        }
    }
    
    var stopRecordingRequested by remember { mutableStateOf(false) }
    var showPreview3d by remember { mutableStateOf(false) }
    val previewSampleFlow = remember {
        MutableSharedFlow<ImuSample>(extraBufferCapacity = 64)
    }
    // Mirror BLE connection for the preview window status label
    var previewBleConnected by remember { mutableStateOf(false) }
    
    Window(
        onCloseRequest = ::exitApplication,
        title = "Gesture Recording Studio" + (currentProjectPath?.let { " - ${File(it).name}" } ?: ""),
        state = rememberWindowState(position = WindowPosition(Alignment.Center)),
        onKeyEvent = { event ->
            if (event.key == Key.Spacebar && event.type == KeyEventType.KeyDown) {
                stopRecordingRequested = true
                true
            } else {
                false
            }
        }
    ) {
        MenuBar {
            Menu("File") {
                Item("New Project...", onClick = { showNewProjectDialog() })
                Item("Open Project...", onClick = { showOpenDialog() })
                Separator()
                Item(
                    "Save",
                    enabled = database != null,
                    onClick = { statusMessage = "Project auto-saves continuously" }
                )
                Item(
                    "Save As...",
                    enabled = database != null,
                    onClick = { showSaveAsDialog() }
                )
                Separator()
                Item(
                    "Export...",
                    enabled = database != null,
                    onClick = { showExportDialog() }
                )
                Separator()
                Item("Settings...", onClick = { showSettings = true })
            }
        }
        
        if (showSettings) {
            SettingsWindow(
                settings = settings,
                onSettingsChange = { newSettings ->
                    settings = newSettings
                    saveSettings(newSettings)
                },
                onCloseRequest = { showSettings = false }
            )
        }

        if (showPreview3d) {
            Preview3DWindow(
                sampleFlow = previewSampleFlow,
                connected = previewBleConnected,
                onCloseRequest = {
                    showPreview3d = false
                    previewBleConnected = false
                }
            )
        }

        LaunchedEffect(showPreview3d) {
            if (!showPreview3d) previewBleConnected = false
        }
        
        App(
            databaseManager = database,
            onStatusUpdate = { statusMessage = it },
            onRequestStop = { 
                val shouldStop = stopRecordingRequested
                if (shouldStop) stopRecordingRequested = false
                shouldStop
            },
            countdownSeconds = settings.countdownSeconds,
            countdownBeepsEnabled = settings.countdownBeepsEnabled,
            preview3dOpen = showPreview3d,
            onPreview3dOpenChange = { showPreview3d = it },
            onPreviewSample = { sample ->
                if (!previewBleConnected) previewBleConnected = true
                previewSampleFlow.tryEmit(sample)
            }
        )
    }
}
