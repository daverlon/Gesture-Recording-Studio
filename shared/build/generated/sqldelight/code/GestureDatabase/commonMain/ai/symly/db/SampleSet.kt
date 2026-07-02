package ai.symly.db

import kotlin.Long
import kotlin.String

public data class SampleSet(
  public val id: String,
  public val gestureId: String,
  public val sourceCaptureId: String,
  public val timestamp: Long,
  public val strategy: String,
  public val sampleMs: Long,
  public val paddingMs: Long,
  public val stepMs: Long?,
  public val randomCount: Long?,
)
