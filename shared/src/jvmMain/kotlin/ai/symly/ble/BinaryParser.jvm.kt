package ai.symly.ble

import ai.symly.ImuSample
import java.nio.ByteBuffer
import java.nio.ByteOrder

actual fun parseImuBinaryData(data: ByteArray): ImuSample {
    val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
    
    // First 4 bytes: timestamp (milliseconds since device boot)
    // Next 36 bytes: sensor data (9 floats)
    val timestampMs = buffer.getInt(0).toLong()

    return ImuSample(
        ax = buffer.getFloat(16),
        ay = buffer.getFloat(20),
        az = buffer.getFloat(24),
        gx = buffer.getFloat(4),
        gy = buffer.getFloat(8),
        gz = buffer.getFloat(12),
        roll = buffer.getFloat(28),
        pitch = buffer.getFloat(32),
        yaw = buffer.getFloat(36),
        timestampMs = timestampMs
    )
}
