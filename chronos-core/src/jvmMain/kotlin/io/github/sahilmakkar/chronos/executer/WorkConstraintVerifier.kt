package io.github.sahilmakkar.chronos.executer

import io.github.sahilmakkar.chronos.task.constraints.TimeWindow
import io.github.sahilmakkar.chronos.db.WorkRequestEntity
import io.github.sahilmakkar.chronos.network.NetworkChecker
import io.github.sahilmakkar.chronos.network.NetworkType
import io.github.sahilmakkar.chronos.utils.KmpClock
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime
import java.io.File

/**
 * Verifies that a [WorkRequestEntity]'s constraints are satisfied before execution.
 * JVM best-effort: charging and battery constraints are not supported and are silently skipped.
 */
 class WorkConstraintVerifier(
    private val networkChecker: NetworkChecker,
    private val clock: KmpClock
) {

    fun isSatisfied(entity: WorkRequestEntity): Boolean {
        if (!verifyNetwork(entity)) return false
        if (entity.requiresStorageNotLow && !isStorageNotLow()) return false
        if (!verifyTimeWindow(entity)) return false
        return true
    }

    private fun verifyNetwork(entity: WorkRequestEntity): Boolean {
        val netType = NetworkType.valueOf(entity.networkType)
        if (netType == NetworkType.CONNECTED && !networkChecker.isConnected()) return false
        if (netType == NetworkType.UNMETERED && !networkChecker.isUnmetered()) return false
        return true
    }

    private fun verifyTimeWindow(entity: WorkRequestEntity): Boolean {
        val start = entity.windowStartHour ?: return true
        val end = entity.windowEndHour ?: return true
        val tz = entity.windowTimeZone ?: return true
        return isInsideTimeWindow(TimeWindow(start, end, tz), clock.now())
    }

    private fun isStorageNotLow(): Boolean =
        File(System.getProperty("user.home")).usableSpace > 100_000_000L

    private fun isInsideTimeWindow(window: TimeWindow, now: Instant): Boolean {
        val hour = now.toLocalDateTime(kotlinx.datetime.TimeZone.of(window.timeZone)).hour
        return if (window.startHour <= window.endHour) {
            hour in window.startHour until window.endHour
        } else {
            hour >= window.startHour || hour < window.endHour
        }
    }
}