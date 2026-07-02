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

const val IMU_SYNC_BYTE_0: Byte = 0x53 // 'S'
const val IMU_SYNC_BYTE_1: Byte = 0x59 // 'Y'
const val IMU_PAYLOAD_BYTES = 36
const val IMU_FRAME_BYTES = IMU_PAYLOAD_BYTES + 2 // sync(2) + payload(36)
