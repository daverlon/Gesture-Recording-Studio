package ai.symly.preview

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    fun length(): Float = sqrt(x * x + y * y + z * z)
    fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3): Vec3 = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x
    )
    fun normalized(): Vec3 {
        val len = length()
        return if (len < 1e-8f) ZERO else this * (1f / len)
    }

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
        val UP = Vec3(0f, 0f, 1f) // IMU / Madgwick world up
    }
}

/** Unit quaternion, Hamilton convention (w, x, y, z). */
data class Quat(val w: Float, val x: Float, val y: Float, val z: Float) {
    fun rotate(v: Vec3): Vec3 {
        // v' = q * v * q^-1
        val qx = x
        val qy = y
        val qz = z
        val qw = w
        val tx = 2f * (qy * v.z - qz * v.y)
        val ty = 2f * (qz * v.x - qx * v.z)
        val tz = 2f * (qx * v.y - qy * v.x)
        return Vec3(
            v.x + qw * tx + (qy * tz - qz * ty),
            v.y + qw * ty + (qz * tx - qx * tz),
            v.z + qw * tz + (qx * ty - qy * tx)
        )
    }

    fun normalized(): Quat {
        val len = sqrt(w * w + x * x + y * y + z * z)
        return if (len < 1e-8f) IDENTITY else Quat(w / len, x / len, y / len, z / len)
    }

    companion object {
        val IDENTITY = Quat(1f, 0f, 0f, 0f)

        /**
         * Build quaternion from Madgwick/Arduino Euler angles in degrees.
         * Convention matches Arduino MadgwickAHRS: yaw→pitch→roll (ZYX intrinsic).
         */
        fun fromEulerDegrees(rollDeg: Float, pitchDeg: Float, yawDeg: Float): Quat {
            val r = (rollDeg * (PI.toFloat() / 180f)) * 0.5f
            val p = (pitchDeg * (PI.toFloat() / 180f)) * 0.5f
            val y = (yawDeg * (PI.toFloat() / 180f)) * 0.5f
            val cr = cos(r); val sr = sin(r)
            val cp = cos(p); val sp = sin(p)
            val cy = cos(y); val sy = sin(y)
            return Quat(
                w = cr * cp * cy + sr * sp * sy,
                x = sr * cp * cy - cr * sp * sy,
                y = cr * sp * cy + sr * cp * sy,
                z = cr * cp * sy - sr * sp * cy
            ).normalized()
        }
    }
}
