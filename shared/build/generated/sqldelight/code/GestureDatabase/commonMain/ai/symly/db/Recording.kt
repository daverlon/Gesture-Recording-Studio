package ai.symly.db

import kotlin.Long
import kotlin.String

public data class Recording(
  public val id: String,
  public val gestureId: String,
  public val timestamp: Long,
  public val durationMs: Long,
  public val paddingMs: Long,
  public val sourceCaptureId: String?,
  public val sampleSetId: String?,
  public val offsetMs: Long?,
)
