package ai.symly

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val autoOpenLastProject: Boolean = false,
    val countdownSeconds: Int = 3,
    val countdownBeepsEnabled: Boolean = false
)
