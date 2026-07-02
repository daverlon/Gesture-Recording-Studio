package ai.symly.db

import ai.symly.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProjectManager(private val scope: CoroutineScope) {
    
    private val _currentProjectPath = MutableStateFlow<String?>(null)
    val currentProjectPath: StateFlow<String?> = _currentProjectPath.asStateFlow()
    
    private val _database = MutableStateFlow<DatabaseManager?>(null)
    val database: StateFlow<DatabaseManager?> = _database.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    fun newProject(path: String, driverFactory: (String?) -> app.cash.sqldelight.db.SqlDriver) {
        scope.launch {
            try {
                _isLoading.value = true
                val driver = driverFactory(path)
                val db = GestureDatabase(driver)
                _database.value = DatabaseManager(db)
                _currentProjectPath.value = path
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun openProject(path: String, driverFactory: (String?) -> app.cash.sqldelight.db.SqlDriver) {
        scope.launch {
            try {
                _isLoading.value = true
                val driver = driverFactory(path)
                val db = GestureDatabase(driver)
                _database.value = DatabaseManager(db)
                _currentProjectPath.value = path
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun closeProject() {
        _database.value = null
        _currentProjectPath.value = null
    }
}
