package ai.symly.db

import kotlin.Double
import kotlin.Long
import kotlin.String

public data class ContinuousCaptureSample(
  public val id: Long,
  public val captureId: String,
  public val sequence: Long,
  public val ax: Double,
  public val ay: Double,
  public val az: Double,
  public val gx: Double,
  public val gy: Double,
  public val gz: Double,
  public val roll: Double,
  public val pitch: Double,
  public val yaw: Double,
)
