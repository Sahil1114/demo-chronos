package io.github.sahilmakkar.chronos.constraints

import io.github.sahilmakkar.chronos.port.ConstraintVerifier
import io.github.sahilmakkar.chronos.port.NetworkChecker
import io.github.sahilmakkar.chronos.storage.TaskEntity
import io.github.sahilmakkar.chronos.task.constraints.NetworkType
import io.github.sahilmakkar.chronos.task.constraints.TimeWindow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

internal abstract class BaseConstraintVerifier(
    private val networkChecker: NetworkChecker? = null
) : ConstraintVerifier {

    protected fun verifyNetwork(entity: TaskEntity): Boolean {
        if (entity.networkType == NetworkType.NOT_REQUIRED) return true
        val checker = networkChecker ?: return true
        return when (entity.networkType) {
            NetworkType.NOT_REQUIRED -> true
            NetworkType.CONNECTED -> checker.isConnected()
            NetworkType.UNMETERED -> checker.isConnected() && checker.isUnmetered()
        }
    }

    protected fun verifyTimeWindow(entity: TaskEntity): Boolean {
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
