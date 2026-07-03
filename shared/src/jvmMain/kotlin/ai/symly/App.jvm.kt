package ai.symly

import java.awt.Toolkit

actual fun playBeep() {
    try {
        Toolkit.getDefaultToolkit().beep()
    } catch (_: Exception) {
        // Silently fail if beep not available
    }
}
