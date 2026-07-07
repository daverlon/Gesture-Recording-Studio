package ai.symly

import java.awt.Toolkit
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.sin

private val isMac = System.getProperty("os.name").contains("Mac", ignoreCase = true)

actual fun playBeep() {
    Thread {
        try {
            if (isMac) {
                playSoftTone()
            } else {
                Toolkit.getDefaultToolkit().beep()
            }
        } catch (_: Exception) {
            if (!isMac) {
                try {
                    Toolkit.getDefaultToolkit().beep()
                } catch (_: Exception) {
                }
            }
        }
    }.apply {
        isDaemon = true
        name = "grs-beep"
        start()
    }
}

private fun playSoftTone() {
    val sampleRate = 44100f
    val durationSec = 0.06f
    val frequency = 880.0
    val volume = 0.12f
    val numSamples = (durationSec * sampleRate).toInt()
    val format = AudioFormat(sampleRate, 16, 1, true, false)
    val line = AudioSystem.getSourceDataLine(format)
    line.open(format)
    line.start()

    val buffer = ByteArray(numSamples * 2)
    for (i in 0 until numSamples) {
        val angle = 2.0 * Math.PI * i * frequency / sampleRate
        val sample = (sin(angle) * Short.MAX_VALUE * volume).toInt().toShort()
        buffer[i * 2] = (sample.toInt() and 0xFF).toByte()
        buffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
    }
    line.write(buffer, 0, buffer.size)
    line.drain()
    line.close()
}
