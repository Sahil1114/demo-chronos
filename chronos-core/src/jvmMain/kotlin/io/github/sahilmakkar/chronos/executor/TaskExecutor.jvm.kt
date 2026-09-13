package io.github.sahilmakkar.chronos.db.executor

import io.github.sahilmakkar.chronos.network.NetworkChecker
import io.github.sahilmakkar.chronos.network.NetworkType
import io.github.sahilmakkar.chronos.roomDb.WorkRequestEntity
import io.github.sahilmakkar.chronos.task.constraints.TimeWindow
import kotlinx.datetime.toLocalDateTime
import java.io.File
import kotlin.time.Clock
import kotlin.time.Instant

actual class WorkConstraintVerifier(
    private val networkChecker: NetworkChecker,

    ) {
    actual fun isSatisfied(entity: WorkRequestEntity): Boolean {
        if (!verifyNetwork(entity)) return false
        if (entity.requiresStorageNotLow && !isStorageNotLow()) return false
        if (!verifyTimeWindow(entity)) return false
        return true
    }

    private fun verifyNetwork(entity: WorkRequestEntity): Boolean {
        if (entity.networkType == NetworkType.CONNECTED && !networkChecker.isConnected()) return false
        if (entity.networkType == NetworkType.UNMETERED && !networkChecker.isUnmetered()) return false
        return true
    }

    private fun verifyTimeWindow(entity: WorkRequestEntity): Boolean {
        val start = entity.windowStartHour ?: return true
        val end = entity.windowEndHour ?: return true
        val tz = entity.windowTimeZone ?: return true
        return isInsideTimeWindow(
            window = TimeWindow(startHour = start, endHour = end, timeZone = tz),
            now = Clock.System.now()
        )
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

