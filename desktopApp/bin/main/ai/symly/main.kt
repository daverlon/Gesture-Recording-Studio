package ai.symly

import ai.symly.db.DatabaseManager
import ai.symly.db.DriverFactory
import ai.symly.db.GestureDatabase
import androidx.compose.runtime.*
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

fun main() = application {
    val scope = rememberCoroutineScope()
    
    var currentProjectPath by remember { mutableStateOf<String?>(null) }
    var database by remember { mutableStateOf<DatabaseManager?>(null) }
    var statusMessage by remember { mutableStateOf("No project loaded") }
    
    fun createNewProject(path: String) {
        scope.launch {
            try {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
                val driver = DriverFactory.createDriver(path)
                database = DatabaseManager(GestureDatabase(driver))
                currentProjectPath = path
                statusMessage = "Created project: ${file.name}"
            } catch (e: Exception) {
                statusMessage = "Error creating project: ${e.message}"
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
                statusMessage = "Opened project: ${file.name}"
            } catch (e: Exception) {
                statusMessage = "Error opening project: ${e.message}"
            }
        }
    }
    
    fun showSaveDialog() {
        val dialog = FileDialog(null as Frame?, "Save Project", FileDialog.SAVE)
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
    
    Window(
        onCloseRequest = ::exitApplication,
        title = "Gesture Recording Studio" + (currentProjectPath?.let { " - ${File(it).name}" } ?: ""),
    ) {
        MenuBar {
            Menu("File") {
                Item("New Project...", onClick = { showSaveDialog() })
                Item("Open Project...", onClick = { showOpenDialog() })
                Separator()
                Item(
                    "Save As...",
                    enabled = database != null,
                    onClick = { showSaveDialog() }
                )
            }
        }
        
        App(
            databaseManager = database,
            onStatusUpdate = { statusMessage = it }
        )
    }
}
