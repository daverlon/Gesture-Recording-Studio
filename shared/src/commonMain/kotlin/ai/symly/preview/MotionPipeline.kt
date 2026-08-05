package ai.symly.preview

import ai.symly.ImuSample
import kotlin.math.abs
import kotlin.math.sqrt

private const val FADE_WINDOW_MS = 1680L  // ~30% shorter than 2400ms

/**
 * Sketchbook pose: forearm bent, pointing at the other palm. Tip length is a
 * bit longer so subtler forearm/wrist angles still make readable strokes
 * (path ≈ length × angle).
 */
private const val TIP_LENGTH_M = 0.28f
private val TIP_LOCAL = Vec3(TIP_LENGTH_M, 0f, 0f)
private val POINTING_LOCAL = Vec3(1f, 0f, 0f)

private const val TIP_SMOOTH = 0.4f
private const val STILL_GYRO_THRESH = 12f

private const val PITCH_SIGN = -1f
private const val H_SIGN = -1f

/**
 * Soft-follow the sketchbook frame while idle (re-aim at the palm); barely
 * move it mid-stroke so the letter stays stable.
 */
private const val FORWARD_FOLLOW_STILL = 0.08f
private const val FORWARD_FOLLOW_MOTION = 0.01f
private const val ORIGIN_FOLLOW_STILL = 0.10f
private const val ORIGIN_FOLLOW_MOTION = 0.012f

data class TrailPoint(
    val position: Vec3,
    val timestampMs: Long,
    val alpha: Float
)

data class PreviewPose(
    val position: Vec3,
    val pivot: Vec3,
    val orientation: Quat,
    val accelGMag: Float,
    val gyroMag: Float,
    val stationary: Boolean = false,
    val stroking: Boolean = false
)

/**
 * Hinge tip projected onto a "sketchbook" plane:
 * - plane faces along the forearm (toward the palm you're writing on)
 * - page-up = gravity projected into that plane (fixes bent-arm up/down warp
 *   from assuming a fixed world-vertical wall)
 *
 * Wrist-only and forearm motions both move the tip; mount doesn't need to be
 * perfectly square — Recenter / idle follow re-aims the page.
 */
class MotionPipeline {
    private var planeForward: Vec3? = null
    private var origin: Vec3? = null
    private var smoothed = Vec3.ZERO
    private val trail = ArrayDeque<TrailPoint>()

    var latestPose: PreviewPose = PreviewPose(
        position = Vec3.ZERO,
        pivot = Vec3.ZERO,
        orientation = Quat.IDENTITY,
        accelGMag = 1f,
        gyroMag = 0f
    )
        private set

    fun reset() {
        planeForward = null
        origin = null
        smoothed = Vec3.ZERO
        trail.clear()
        latestPose = PreviewPose(Vec3.ZERO, Vec3.ZERO, Quat.IDENTITY, 1f, 0f)
    }

    fun update(sample: ImuSample): PreviewPose {
        val gyroMag = abs(sample.gx) + abs(sample.gy) + abs(sample.gz)
        val accelGMag = sqrt(sample.ax * sample.ax + sample.ay * sample.ay + sample.az * sample.az)
        val stationary = gyroMag < STILL_GYRO_THRESH

        val orientation = Quat.fromEulerDegrees(
            sample.roll,
            PITCH_SIGN * sample.pitch,
            sample.yaw
        )
        val pointing = orientation.rotate(POINTING_LOCAL).normalized()
        val tip = orientation.rotate(TIP_LOCAL)

        if (planeForward == null) planeForward = pointing
        if (origin == null) {
            origin = tip
            smoothed = Vec3.ZERO
        }

        val fFollow = if (stationary) FORWARD_FOLLOW_STILL else FORWARD_FOLLOW_MOTION
        val oFollow = if (stationary) ORIGIN_FOLLOW_STILL else ORIGIN_FOLLOW_MOTION
        planeForward = nlerp(planeForward!!, pointing, fFollow)
        origin = lerp(origin!!, tip, oFollow)

        val (right, up) = sketchbookBasis(planeForward!!)
        val delta = tip - origin!!
        val planar = Vec3(H_SIGN * delta.dot(right), 0f, delta.dot(up))
        smoothed = lerp(smoothed, planar, TIP_SMOOTH)

        val nowMs = sample.timestampMs
        trail.addLast(TrailPoint(smoothed, nowMs, 1f))
        purgeTrail(nowMs)

        latestPose = PreviewPose(
            position = smoothed,
            pivot = Vec3.ZERO,
            orientation = orientation,
            accelGMag = accelGMag,
            gyroMag = gyroMag,
            stationary = stationary,
            stroking = !stationary
        )
        return latestPose
    }

    fun visibleTrail(nowMs: Long = latestPoseTimestamp()): List<TrailPoint> {
        purgeTrail(nowMs)
        return trail.map { pt ->
            val age = (nowMs - pt.timestampMs).coerceAtLeast(0L)
            val alpha = (1f - age.toFloat() / FADE_WINDOW_MS).coerceIn(0f, 1f)
            pt.copy(alpha = alpha)
        }
    }

    /**
     * Sketchbook page: normal = forearm pointing at the palm.
     * Page-up = world gravity flattened onto the page (not raw world Z).
     */
    private fun sketchbookBasis(forward: Vec3): Pair<Vec3, Vec3> {
        val f = forward.normalized()
        var up = Vec3.UP - f * Vec3.UP.dot(f)
        if (up.length() < 0.25f) {
            // Forearm nearly vertical — pick a stable fallback horizontal
            val fallback = Vec3(0f, 1f, 0f)
            up = fallback - f * fallback.dot(f)
        }
        up = up.normalized()
        var right = up.cross(f)
        if (right.length() < 1e-3f) {
            right = Vec3(1f, 0f, 0f).cross(f)
        }
        right = right.normalized()
        // Re-orthogonalize up for a clean plane
        up = f.cross(right).normalized()
        return right to up
    }

    private fun latestPoseTimestamp(): Long = trail.lastOrNull()?.timestampMs ?: 0L

    private fun purgeTrail(nowMs: Long) {
        while (trail.isNotEmpty() && nowMs - trail.first().timestampMs > FADE_WINDOW_MS) {
            trail.removeFirst()
        }
    }
}

private fun lerp(a: Vec3, b: Vec3, t: Float): Vec3 = a + (b - a) * t

private fun nlerp(a: Vec3, b: Vec3, t: Float): Vec3 = (a + (b - a) * t).normalized()
