package io.github.sahilmakkar.chronos.executor

import io.github.sahilmakkar.chronos.roomDb.WorkRequestEntity
import io.github.sahilmakkar.chronos.task.constraints.TimeWindow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

actual class WorkConstraintVerifier {

    actual fun isSatisfied(entity: WorkRequestEntity): Boolean = verifyTimeWindow(entity)

    private fun verifyTimeWindow(entity: WorkRequestEntity): Boolean {
        val start = entity.windowStartHour ?: return true
        val end = entity.windowEndHour ?: return true
        val timeZone = entity.windowTimeZone ?: return true
        return runCatching {
            isInsideTimeWindow(
                window = TimeWindow(startHour = start, endHour = end, timeZone = timeZone),
                now = Clock.System.now()
            )
        }.getOrDefault(false)
    }

    private fun isInsideTimeWindow(window: TimeWindow, now: Instant): Boolean {
        val hour = now.toLocalDateTime(TimeZone.of(window.timeZone)).hour
        return if (window.startHour <= window.endHour) {
            hour in window.startHour until window.endHour
        } else {
            hour >= window.startHour || hour < window.endHour
        }
    }
}