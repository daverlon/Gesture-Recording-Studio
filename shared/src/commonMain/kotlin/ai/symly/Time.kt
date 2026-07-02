package ai.symly

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalUuidApi::class)
fun newId(): String = Uuid.random().toString()

fun nowMs(): Long = System.currentTimeMillis()

fun formatTimestamp(ts: Long): String {
    val time = Instant.fromEpochMilliseconds(ts).toLocalDateTime(TimeZone.currentSystemDefault()).time
    return buildString {
        append(time.hour.toString().padStart(2, '0'))
        append(':')
        append(time.minute.toString().padStart(2, '0'))
        append(':')
        append(time.second.toString().padStart(2, '0'))
    }
}
