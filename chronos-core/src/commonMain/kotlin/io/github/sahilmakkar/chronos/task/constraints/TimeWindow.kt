package io.github.sahilmakkar.chronos.task.constraints

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Permitted time of day window in which a task may be executed.
 *
 * @property startHour The beginning hour of the allowed window (0-23).
 * @property endHour The ending hour of the allowed window (0-24).
 * @property timeZone Timezone identifier (defaults to "UTC").
 */
public data class TimeWindow(
    public val startHour: Int,
    public val endHour: Int,
    public val timeZone: String = "UTC"
) {
    init {
        require(startHour in 0..23) {
            "startHour must be between 0 and 23, got $startHour"
        }
        require(endHour in 0..24) {
            "endHour must be between 0 and 24, got $endHour"
        }
        require(startHour != endHour) {
            "startHour ($startHour) must not equal endHour ($endHour)"
        }
    }

    /**
     * Checks if the given instant falls within this time window.
     */
    public fun contains(now: Instant): Boolean {
        return runCatching {
            val hour = now.toLocalDateTime(TimeZone.of(zoneId = timeZone)).hour
            if (startHour <= endHour) {
                hour in startHour until endHour
            } else {
                hour !in endHour..<startHour
            }
        }.getOrDefault(defaultValue = false)
    }
}
