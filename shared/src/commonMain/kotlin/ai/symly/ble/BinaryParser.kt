package ai.symly.ble

import ai.symly.ImuSample

expect fun parseImuBinaryData(data: ByteArray): ImuSample

fun parseImuBinaryFrameOrNull(data: ByteArray): ImuSample? {
    if (data.size != IMU_PAYLOAD_BYTES) return null
    return try {
        parseImuBinaryData(data)
    } catch (_: Exception) {
        null
    }
}

const val IMU_PAYLOAD_BYTES = 40 // 4 bytes timestamp + 36 bytes sensor data
