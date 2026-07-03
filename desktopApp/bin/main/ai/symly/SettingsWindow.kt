package ai.symly

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState

private val AppFont = androidx.compose.ui.text.font.FontFamily.SansSerif

private object SettingsType {
    val categorySelected = TextStyle(
        fontFamily = AppFont,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = Color(0xFF1A1A1A)
    )
    val category = TextStyle(
        fontFamily = AppFont,
        fontSize = 12.sp,
        color = Color(0xFF6B6B6B)
    )
    val sectionTitle = TextStyle(
        fontFamily = AppFont,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF1A1A1A)
    )
    val label = TextStyle(
        fontFamily = AppFont,
        fontSize = 12.sp,
        color = Color(0xFF1A1A1A)
    )
    val description = TextStyle(
        fontFamily = AppFont,
        fontSize = 11.sp,
        color = Color(0xFF6B6B6B)
    )
}

enum class SettingsCategory {
    GENERAL,
    TIMED_RECORDINGS
}

@Composable
fun SettingsWindow(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onCloseRequest: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf(SettingsCategory.GENERAL) }
    var currentSettings by remember { mutableStateOf(settings) }

    Window(
        onCloseRequest = {
            onSettingsChange(currentSettings)
            onCloseRequest()
        },
        title = "Settings",
        state = rememberWindowState(
            position = WindowPosition(Alignment.Center),
            width = 700.dp,
            height = 500.dp
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5))
        ) {
            // Left sidebar - categories
            Column(
                modifier = Modifier
                    .width(200.dp)
                    .fillMaxHeight()
                    .background(Color.White)
                    .padding(vertical = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                CategoryItem(
                    text = "General",
                    selected = selectedCategory == SettingsCategory.GENERAL,
                    onClick = { selectedCategory = SettingsCategory.GENERAL }
                )
                CategoryItem(
                    text = "Timed Recordings",
                    selected = selectedCategory == SettingsCategory.TIMED_RECORDINGS,
                    onClick = { selectedCategory = SettingsCategory.TIMED_RECORDINGS }
                )
            }

            // Right content area
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when (selectedCategory) {
                    SettingsCategory.GENERAL -> {
                        GeneralSettings(
                            settings = currentSettings,
                            onSettingsChange = { currentSettings = it }
                        )
                    }
                    SettingsCategory.TIMED_RECORDINGS -> {
                        TimedRecordingsSettings(
                            settings = currentSettings,
                            onSettingsChange = { currentSettings = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Color(0xFFE8E8E8) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            style = if (selected) SettingsType.categorySelected else SettingsType.category
        )
    }
}

@Composable
private fun GeneralSettings(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("General", style = SettingsType.sectionTitle)
        
        SettingCheckbox(
            label = "Automatically open last project",
            description = "Open the most recently used project when the app starts",
            checked = settings.autoOpenLastProject,
            onCheckedChange = { 
                onSettingsChange(settings.copy(autoOpenLastProject = it))
            }
        )
    }
}

@Composable
private fun TimedRecordingsSettings(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Timed Recordings", style = SettingsType.sectionTitle)
        
        SettingNumberInput(
            label = "Countdown seconds",
            description = "Duration of countdown before each timed recording starts",
            value = settings.countdownSeconds,
            onValueChange = { value ->
                if (value in 1..10) {
                    onSettingsChange(settings.copy(countdownSeconds = value))
                }
            },
            range = 1..10
        )
    }
}

@Composable
private fun SettingCheckbox(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFF2196F3),
                uncheckedColor = Color(0xFFCCCCCC)
            )
        )
        Column {
            Text(label, style = SettingsType.label)
            Text(description, style = SettingsType.description)
        }
    }
}

@Composable
private fun SettingNumberInput(
    label: String,
    description: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = SettingsType.label)
        Text(description, style = SettingsType.description)
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = value.toString(),
                onValueChange = { text ->
                    text.toIntOrNull()?.let { newValue ->
                        if (newValue in range) {
                            onValueChange(newValue)
                        }
                    }
                },
                modifier = Modifier.width(80.dp),
                textStyle = SettingsType.label,
                singleLine = true,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = Color(0xFF1A1A1A),
                    backgroundColor = Color.White,
                    focusedBorderColor = Color(0xFF2196F3),
                    unfocusedBorderColor = Color(0xFFCCCCCC)
                )
            )
            Text("seconds", style = SettingsType.description)
        }
    }
}
