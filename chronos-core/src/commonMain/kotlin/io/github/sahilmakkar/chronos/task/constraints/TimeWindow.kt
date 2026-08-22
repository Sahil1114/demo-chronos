package io.github.sahilmakkar.chronos.task.constraints

/**
 * Specifies a scheduled daily window inside which the task is allowed to execute.
 *
 * @property startHour The starting hour of the allowed window (0-23, inclusive).
 * @property endHour The ending hour of the allowed window (0-23, exclusive).
 * @property timeZone The timezone identifier (e.g., "UTC", "Asia/Kolkata") for the window.
 */
data class TimeWindow(
    val startHour: Int,
    val endHour: Int,
    val timeZone: String = "UTC"
) {
    init {
        require(startHour in 0..23) { "startHour must be between 0 and 23, got $startHour" }
        require(endHour in 0..24) { "endHour must be between 0 and 24, got $endHour" }
        require(startHour < endHour) { "startHour ($startHour) must be before endHour ($endHour)" }
    }
}
