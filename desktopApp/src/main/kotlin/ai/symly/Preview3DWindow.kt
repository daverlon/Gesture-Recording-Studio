package ai.symly

import ai.symly.preview.MotionPipeline
import ai.symly.preview.Preview3DScene
import ai.symly.preview.PreviewSceneState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.flow.SharedFlow

@Composable
fun Preview3DWindow(
    sampleFlow: SharedFlow<ImuSample>,
    connected: Boolean,
    onCloseRequest: () -> Unit
) {
    val pipeline = remember { MotionPipeline() }
    var sceneState by remember { mutableStateOf(PreviewSceneState()) }

    LaunchedEffect(sampleFlow) {
        sampleFlow.collect { sample ->
            val pose = pipeline.update(sample)
            sceneState = PreviewSceneState(
                pose = pose,
                trail = pipeline.visibleTrail(sample.timestampMs)
            )
        }
    }

    Window(
        onCloseRequest = onCloseRequest,
        title = "3D Preview",
        state = rememberWindowState(
            position = WindowPosition(Alignment.Center),
            width = 900.dp,
            height = 640.dp
        )
    ) {
        Column(modifier = Modifier.fillMaxSize().background(Color(0xFF2A2A2A))) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(Color(0xFF1E1E1E))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when {
                        !connected -> "Waiting for device…"
                        sceneState.pose.stroking -> "Drawing · stroke"
                        sceneState.pose.stationary -> "Drawing · idle (adapting)"
                        else -> "Drawing"
                    },
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 11.sp,
                        color = when {
                            !connected -> Color(0xFF9A9A9A)
                            sceneState.pose.stroking -> Color(0xFFC98A1E)
                            sceneState.pose.stationary -> Color(0xFF2E9E4F)
                            else -> Color(0xFFAAAAAA)
                        }
                    )
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "|a|=${"%.2f".format(sceneState.pose.accelGMag)}g  " +
                            "|g|=${"%.0f".format(sceneState.pose.gyroMag)}°/s",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color(0xFF888888)
                        )
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF2B5FD9))
                            .clickable {
                                pipeline.reset()
                                sceneState = PreviewSceneState()
                            }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Recenter",
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        )
                    }
                }
            }
            Preview3DScene(
                state = sceneState,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
            Text(
                text = "Sketchbook mode: page faces your forearm (toward the palm). Page-up from " +
                    "gravity. Hold still / Recenter to re-aim. Trail ~1.7s — use the 2D inset.",
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF777777)
                ),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}
