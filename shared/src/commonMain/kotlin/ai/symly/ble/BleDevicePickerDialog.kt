package ai.symly.ble

import ai.symly.CompactButton
import ai.symly.Divider
import ai.symly.compactClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DialogBorder = Color(0xFFE0E0E0)
private val DialogTitle = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A))
private val DialogBody = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, color = Color(0xFF1A1A1A))
private val DialogLabel = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, color = Color(0xFF6B6B6B))
private val DialogMonoSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF555555))

@Composable
fun BleDevicePickerDialog(
    bleClient: BleClient,
    onDismiss: () -> Unit,
    onDeviceSelected: (BleDevice) -> Unit
) {
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf(listOf<BleDevice>()) }

    LaunchedEffect(Unit) {
        bleClient.startScan(scope) { found ->
            devices = found
        }
    }

    DisposableEffect(Unit) {
        onDispose { bleClient.stopScan() }
    }

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
                .width(340.dp)
                .border(1.dp, DialogBorder, RoundedCornerShape(4.dp))
                .background(Color.White, RoundedCornerShape(4.dp))
                .clickable(
                    indication = null,
                    interactionSource = panelInteraction,
                    onClick = {}
                )
                .padding(12.dp)
        ) {
            Text("Bluetooth devices", style = DialogTitle)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Select a device to connect.", style = DialogLabel)
            Spacer(modifier = Modifier.height(12.dp))

            if (devices.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Scanning...", style = DialogLabel)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                ) {
                    items(devices, key = { it.id }) { device ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .compactClickable { onDeviceSelected(device) }
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(device.name, style = DialogBody.copy(fontWeight = FontWeight.Medium))
                                if (device.rssi != null) {
                                    Text("${device.rssi} dBm", style = DialogMonoSmall)
                                }
                            }
                        }
                        Divider()
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                CompactButton(text = "Cancel", onClick = onDismiss)
            }
        }
    }
}
