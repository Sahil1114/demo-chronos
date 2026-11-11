package io.github.sahilmakkar.chronos.task.constraints

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Permitted time-of-day execution window in which a task may be dispatched.
 *
 * Useful for scheduling maintenance, synchronization, or analytics jobs during off-peak hours
 * (such as overnight between 02:00 and 05:00).
 *
 * ### Window Wrapping:
 * - **Same-day window (`startHour < endHour`):** E.g. `startHour = 9, endHour = 17` permits execution between 09:00 and 17:00.
 * - **Overnight wrap-around window (`startHour > endHour`):** E.g. `startHour = 22, endHour = 6` permits execution
 *   from 22:00 at night until 06:00 the following morning.
 *
 * @property startHour The beginning hour of the allowed window (0..23, inclusive).
 * @property endHour The ending hour of the allowed window (0..24, exclusive).
 * @property timeZone Timezone identifier string used to resolve local hours (defaults to "UTC").
 */
public data class TimeWindow(
    public val startHour: Int,
    public val endHour: Int,
    public val timeZone: String = "UTC"
) {
    init {
        require(value = startHour in 0..23) {
            "startHour must be between 0 and 23, got $startHour"
        }
        require(value = endHour in 0..24) {
            "endHour must be between 0 and 24, got $endHour"
        }
        require(value = startHour != endHour) {
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
