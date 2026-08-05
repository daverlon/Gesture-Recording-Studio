package ai.symly.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

// World: Y-up (Unity-like). IMU Z-up positions are remapped: (x,y,z)_imu → (x,z,y)_display
private const val DISPLAY_SCALE = 12f // meters → scene units (stylus tip ~0.18 m)
private const val GRID_HALF = 4
private const val GRID_STEP = 1f
private const val CAMERA_DISTANCE = 6f
private const val CAMERA_YAW_DEG = 45f
private const val CAMERA_PITCH_DEG = 30f
private const val ORBIT_SENSITIVITY = 0.35f
private const val PITCH_MIN = -89f
private const val PITCH_MAX = 89f

private val BgTop = Color(0xFF3D3D3D)
private val BgBottom = Color(0xFF2A2A2A)
private val GridColor = Color(0xFF555555)
private val AxisX = Color(0xFFE74C3C)
private val AxisY = Color(0xFF2ECC71)
private val AxisZ = Color(0xFF3498DB)
private val TrailColor = Color(0xFF5DADE2)
private val MarkerColor = Color(0xFFF5F5F5)
private val MarkerAccent = Color(0xFFFFB347)
private val ArmColor = Color(0x66FFFFFF)
private val InsetBg = Color(0xF0121212)
private val InsetBorder = Color(0xFF7EB0FF)
private val InsetGrid = Color(0xFF3A3A3A)
private val InsetLabel = Color(0xFFB0B0B0)

private val DefaultPose = PreviewPose(
    position = Vec3.ZERO,
    pivot = Vec3.ZERO,
    orientation = Quat.IDENTITY,
    accelGMag = 1f,
    gyroMag = 0f
)

data class PreviewSceneState(
    val pose: PreviewPose = DefaultPose,
    val trail: List<TrailPoint> = emptyList()
)

@Composable
fun Preview3DScene(
    state: PreviewSceneState,
    modifier: Modifier = Modifier
) {
    var yawDeg by remember { mutableStateOf(CAMERA_YAW_DEG) }
    var pitchDeg by remember { mutableStateOf(CAMERA_PITCH_DEG) }

    Box(
        modifier = modifier
            .background(BgBottom)
            .fillMaxSize()
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { _, dragAmount ->
                        yawDeg -= dragAmount.x * ORBIT_SENSITIVITY
                        pitchDeg = (pitchDeg + dragAmount.y * ORBIT_SENSITIVITY)
                            .coerceIn(PITCH_MIN, PITCH_MAX)
                    }
                }
        ) {
            val cam = Camera(
                yawDeg = yawDeg,
                pitchDeg = pitchDeg,
                distance = CAMERA_DISTANCE,
                viewportW = size.width,
                viewportH = size.height
            )

            // Soft horizon split
            drawRect(BgTop, size = size.copy(height = size.height * 0.45f))
            drawRect(
                BgBottom,
                topLeft = Offset(0f, size.height * 0.45f),
                size = size.copy(height = size.height * 0.55f)
            )

            drawGrid(cam)
            drawAxes(cam)
            drawStylusArm(cam, state.pose)
            drawTrail(cam, state.trail)
            drawMarker(cam, state.pose)
        }

        AxisLegend(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        )

        // ~1/6 of window area: square ≈ 0.41 of the shorter side
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val side = minOf(maxWidth, maxHeight) * 0.41f
            Drawing2DInset(
                state = state,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .size(side)
            )
        }
    }
}

/**
 * Orthographic view of the writing-plane coords (pos.x = horizontal, pos.z = up).
 * Bypasses the 3D camera so we can tell pipeline distortion from perspective.
 */
@Composable
private fun Drawing2DInset(
    state: PreviewSceneState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(InsetBg)
            .border(1.dp, InsetBorder, RoundedCornerShape(4.dp))
    ) {
        Text(
            text = "2D plane",
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = InsetLabel
            ),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
        ) {
            val pad = 10f
            val w = size.width
            val h = size.height
            drawRect(Color(0xFF1A1A1A))

            // Crosshair
            drawLine(InsetGrid, Offset(w * 0.5f, pad), Offset(w * 0.5f, h - pad), strokeWidth = 1f)
            drawLine(InsetGrid, Offset(pad, h * 0.5f), Offset(w - pad, h * 0.5f), strokeWidth = 1f)

            val points = state.trail.map { it.position } + state.pose.position
            var minX = 0f
            var maxX = 0f
            var minZ = 0f
            var maxZ = 0f
            for (p in points) {
                if (p.x < minX) minX = p.x
                if (p.x > maxX) maxX = p.x
                if (p.z < minZ) minZ = p.z
                if (p.z > maxZ) maxZ = p.z
            }
            // Always include origin; keep a usable minimum span
            val spanX = max(maxX - minX, 0.04f)
            val spanZ = max(maxZ - minZ, 0.04f)
            val span = max(spanX, spanZ) * 1.25f
            val cx = (minX + maxX) * 0.5f
            val cz = (minZ + maxZ) * 0.5f
            val scale = minOf(w - 2 * pad, h - 2 * pad) / span

            fun toScreen(p: Vec3): Offset = Offset(
                x = w * 0.5f + (p.x - cx) * scale,
                y = h * 0.5f - (p.z - cz) * scale // z-up → screen-up
            )

            val trail = state.trail
            if (trail.size >= 2) {
                for (i in 1 until trail.size) {
                    val a = trail[i - 1]
                    val b = trail[i]
                    val alpha = ((a.alpha + b.alpha) * 0.5f).coerceIn(0f, 1f)
                    if (alpha < 0.02f) continue
                    drawLine(
                        color = TrailColor.copy(alpha = alpha),
                        start = toScreen(a.position),
                        end = toScreen(b.position),
                        strokeWidth = 2.5f,
                        cap = StrokeCap.Round
                    )
                }
            }

            val cursor = toScreen(state.pose.position)
            drawCircle(MarkerAccent, radius = 5f, center = cursor)
            drawCircle(Color.White, radius = 2f, center = cursor)
        }
    }
}

@Composable
private fun AxisLegend(modifier: Modifier = Modifier) {
    val labelStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = Color(0xFFE8E8E8)
    )
    val hintStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 10.sp,
        color = Color(0xFFAAAAAA)
    )
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xCC141414))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        LegendRow(AxisX, "X", "right", labelStyle)
        LegendRow(AxisY, "Y", "up (gravity)", labelStyle)
        LegendRow(AxisZ, "Z", "forward", labelStyle)
        Text("Sketchbook plane · forearm aim", style = hintStyle, modifier = Modifier.padding(top = 4.dp))
        Text("Left-drag to orbit", style = hintStyle)
    }
}

@Composable
private fun LegendRow(color: Color, axis: String, meaning: String, style: TextStyle) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 1.dp)
    ) {
        Box(modifier = Modifier.size(width = 14.dp, height = 3.dp).background(color))
        Spacer(Modifier.width(8.dp))
        Text("$axis — $meaning", style = style)
    }
}

private class Camera(
    yawDeg: Float,
    pitchDeg: Float,
    val distance: Float,
    val viewportW: Float,
    val viewportH: Float
) {
    private val yaw = yawDeg * (PI.toFloat() / 180f)
    private val pitch = pitchDeg * (PI.toFloat() / 180f)
    private val cosY = cos(yaw)
    private val sinY = sin(yaw)
    private val cosP = cos(pitch)
    private val sinP = sin(pitch)
    private val focal = minOf(viewportW, viewportH) * 0.9f

    /** Project world point (Y-up) to screen pixels. */
    fun project(world: Vec3): Offset? {
        // Orbit camera around origin looking at origin
        val x1 = world.x * cosY + world.z * sinY
        val z1 = -world.x * sinY + world.z * cosY
        val y2 = world.y * cosP - z1 * sinP
        val z2 = world.y * sinP + z1 * cosP
        val depth = z2 + distance
        if (depth < 0.15f) return null
        val sx = viewportW * 0.5f + (x1 * focal) / depth
        val sy = viewportH * 0.5f - (y2 * focal) / depth
        return Offset(sx, sy)
    }
}

/** IMU Z-up (x,y,z) → display Y-up (x, z, y), scaled for visibility. */
private fun toDisplay(p: Vec3): Vec3 = Vec3(p.x * DISPLAY_SCALE, p.z * DISPLAY_SCALE, p.y * DISPLAY_SCALE)

private fun DrawScope.drawGrid(cam: Camera) {
    val half = GRID_HALF
    for (i in -half..half) {
        val a = i * GRID_STEP
        val color = if (i == 0) GridColor.copy(alpha = 0.55f) else GridColor.copy(alpha = 0.35f)
        strokeLine(cam, Vec3(-half.toFloat(), 0f, a), Vec3(half.toFloat(), 0f, a), color, 1f)
        strokeLine(cam, Vec3(a, 0f, -half.toFloat()), Vec3(a, 0f, half.toFloat()), color, 1f)
    }
}

private fun DrawScope.drawAxes(cam: Camera) {
    val len = 1.5f
    strokeLine(cam, Vec3.ZERO, Vec3(len, 0f, 0f), AxisX, 2.5f)
    strokeLine(cam, Vec3.ZERO, Vec3(0f, len, 0f), AxisY, 2.5f)
    strokeLine(cam, Vec3.ZERO, Vec3(0f, 0f, len), AxisZ, 2.5f)
}

private fun DrawScope.drawTrail(cam: Camera, trail: List<TrailPoint>) {
    if (trail.size < 2) return
    for (i in 1 until trail.size) {
        val a = trail[i - 1]
        val b = trail[i]
        val alpha = ((a.alpha + b.alpha) * 0.5f).coerceIn(0f, 1f)
        if (alpha < 0.02f) continue
        strokeLine(
            cam,
            toDisplay(a.position),
            toDisplay(b.position),
            TrailColor.copy(alpha = alpha),
            strokeWidth = 2.5f
        )
    }
}

private fun DrawScope.drawStylusArm(cam: Camera, pose: PreviewPose) {
    strokeLine(cam, toDisplay(pose.pivot), toDisplay(pose.position), ArmColor, 1.5f)
    cam.project(toDisplay(pose.pivot))?.let { drawCircle(Color(0xFF888888), radius = 3f, center = it) }
}

private fun DrawScope.drawMarker(cam: Camera, pose: PreviewPose) {
    val tipPos = toDisplay(pose.position)
    // Small orientation cone at the virtual tip
    val forwardImu = pose.orientation.rotate(Vec3(0.03f, 0f, 0f))
    val upImu = pose.orientation.rotate(Vec3(0f, 0f, 0.02f))
    val coneTip = toDisplay(pose.position + forwardImu)
    val left = toDisplay(pose.position - forwardImu * 0.4f + upImu)
    val right = toDisplay(pose.position - forwardImu * 0.4f - upImu)

    strokeLine(cam, tipPos, coneTip, MarkerAccent, 2.5f)
    val p0 = cam.project(tipPos) ?: return
    val pTip = cam.project(coneTip)
    val pL = cam.project(left)
    val pR = cam.project(right)
    drawCircle(MarkerColor, radius = 5f, center = p0)
    if (pTip != null && pL != null && pR != null) {
        val path = Path().apply {
            moveTo(pTip.x, pTip.y)
            lineTo(pL.x, pL.y)
            lineTo(pR.x, pR.y)
            close()
        }
        drawPath(path, MarkerAccent.copy(alpha = 0.85f))
        drawPath(path, MarkerColor.copy(alpha = 0.9f), style = Stroke(width = 1.2f))
    }
}

private fun DrawScope.strokeLine(
    cam: Camera,
    a: Vec3,
    b: Vec3,
    color: Color,
    strokeWidth: Float
) {
    val pa = cam.project(a) ?: return
    val pb = cam.project(b) ?: return
    drawLine(
        color = color,
        start = pa,
        end = pb,
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
}
