package io.github.sahilmakkar.chronos.executor

import io.github.sahilmakkar.chronos.network.NetworkChecker
import io.github.sahilmakkar.chronos.network.NetworkType
import io.github.sahilmakkar.chronos.roomDb.WorkRequestEntity
import io.github.sahilmakkar.chronos.task.constraints.TimeWindow
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSNumber
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceBatteryState
import kotlin.time.Clock
import kotlin.time.Instant

actual class WorkConstraintVerifier(
    private val networkChecker: NetworkChecker,
) {

    init {
        UIDevice.currentDevice.batteryMonitoringEnabled = true
    }

    actual fun isSatisfied(entity: WorkRequestEntity): Boolean {
        if (!verifyNetwork(entity)) return false
        if (entity.requiresCharging && !isCharging()) return false
        if (entity.requiresBatteryNotLow && !isBatteryNotLow()) return false
        if (entity.requiresStorageNotLow && !isStorageNotLow()) return false
        if (!verifyTimeWindow(entity)) return false
        return true
    }

    private fun verifyNetwork(entity: WorkRequestEntity): Boolean {
        return when (entity.networkType) {
            NetworkType.NOT_REQUIRED -> true
            NetworkType.CONNECTED -> networkChecker.isConnected()
            NetworkType.UNMETERED -> networkChecker.isConnected() && networkChecker.isUnmetered()
        }
    }

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

    private fun isCharging(): Boolean {
        val state = UIDevice.currentDevice.batteryState
        return state == UIDeviceBatteryState.UIDeviceBatteryStateCharging ||
                state == UIDeviceBatteryState.UIDeviceBatteryStateFull
    }

    private fun isBatteryNotLow(): Boolean {
        val level = UIDevice.currentDevice.batteryLevel
        return level !in 0.0f..<0.20f
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun isStorageNotLow(): Boolean {
        val attributes = NSFileManager.defaultManager.attributesOfFileSystemForPath(
            NSHomeDirectory(),
            error = null
        )
        val freeSize =
            (attributes?.get(NSFileSystemFreeSize) as? NSNumber)?.longLongValue ?: return true
        return freeSize > 100_000_000L
    }
}