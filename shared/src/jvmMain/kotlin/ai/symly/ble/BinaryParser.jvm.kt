package ai.symly.ble

import ai.symly.ImuSample
import java.nio.ByteBuffer
import java.nio.ByteOrder

actual fun parseImuBinaryData(data: ByteArray): ImuSample {
    val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

    return ImuSample(
        gx = buffer.getFloat(0),
        gy = buffer.getFloat(4),
        gz = buffer.getFloat(8),
        ax = buffer.getFloat(12),
        ay = buffer.getFloat(16),
        az = buffer.getFloat(20),
        roll = buffer.getFloat(24),
        pitch = buffer.getFloat(28),
        yaw = buffer.getFloat(32)
    )
}
