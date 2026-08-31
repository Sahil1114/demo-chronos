@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.sahilmakkar.chronos.executor

import io.github.sahilmakkar.chronos.task.constraints.TimeWindow
import io.github.sahilmakkar.chronos.db.WorkRequestEntity
import io.github.sahilmakkar.chronos.network.NetworkChecker
import io.github.sahilmakkar.chronos.network.NetworkType
import io.github.sahilmakkar.chronos.utils.KmpClock
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.*
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceBatteryState

/**
 * Verifies that a [WorkRequestEntity]'s constraints are satisfied before execution on iOS.
 * Checks network, battery, charging, storage, and time window constraints natively.
 */
class WorkConstraintVerifier(
    private val networkChecker: NetworkChecker,
    private val clock: KmpClock
) {

    fun isSatisfied(entity: WorkRequestEntity): Boolean {
        if (!verifyNetwork(entity)) return false
        if (entity.requiresCharging && !isCharging()) return false
        if (entity.requiresBatteryNotLow && !isBatteryNotLow()) return false
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
        return isInsideTimeWindow(TimeWindow(start, end, tz), clock.now())
    }

    private fun isInsideTimeWindow(window: TimeWindow, now: Instant): Boolean {
        val hour = now.toLocalDateTime(kotlinx.datetime.TimeZone.of(window.timeZone)).hour
        return if (window.startHour <= window.endHour) {
            hour in window.startHour until window.endHour
        } else {
            hour >= window.startHour || hour < window.endHour
        }
    }

    private fun isCharging(): Boolean {
        val device = UIDevice.currentDevice
        device.batteryMonitoringEnabled = true
        return device.batteryState == UIDeviceBatteryState.UIDeviceBatteryStateCharging ||
                device.batteryState == UIDeviceBatteryState.UIDeviceBatteryStateFull
    }

    private fun isBatteryNotLow(): Boolean {
        val device = UIDevice.currentDevice
        device.batteryMonitoringEnabled = true
        return device.batteryLevel < 0.0f || device.batteryLevel >= 0.20f
    }

    private fun isStorageNotLow(): Boolean {
        val attributes = NSFileManager.defaultManager
            .attributesOfFileSystemForPath(NSHomeDirectory(), error = null)
        val freeSize = attributes?.get(NSFileSystemFreeSize) as? Long ?: return true
        return freeSize > 100_000_000L
    }
}