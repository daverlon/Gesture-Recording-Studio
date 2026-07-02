package ai.symly.db

import kotlin.Long
import kotlin.String

public data class ContinuousCapture(
  public val id: String,
  public val gestureId: String,
  public val timestamp: Long,
  public val durationMs: Long,
)
