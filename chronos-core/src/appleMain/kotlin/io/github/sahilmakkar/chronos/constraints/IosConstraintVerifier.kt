package io.github.sahilmakkar.chronos.constraints

import io.github.sahilmakkar.chronos.port.NetworkChecker
import io.github.sahilmakkar.chronos.storage.TaskEntity
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSNumber
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceBatteryState
import kotlin.time.Clock

/**
 * iOS implementation of [ConstraintVerifier] evaluating device state and network connectivity.
 */
internal class IosConstraintVerifier(
    networkChecker: NetworkChecker,
    clock: Clock = Clock.System
) : BaseConstraintVerifier(networkChecker, clock) {

    private fun ensureBatteryMonitoring() {
        if (!UIDevice.currentDevice.batteryMonitoringEnabled) {
            UIDevice.currentDevice.batteryMonitoringEnabled = true
        }
    }

    override fun isSatisfied(entity: TaskEntity): Boolean {
        if (!verifyNetwork(entity)) return false
        if (entity.requiresCharging && !isCharging()) return false
        if (entity.requiresBatteryNotLow && !isBatteryNotLow()) return false
        if (entity.requiresStorageNotLow && !isStorageNotLow()) return false
        if (!verifyTimeWindow(entity)) return false
        return true
    }

    private fun isCharging(): Boolean {
        ensureBatteryMonitoring()
        val state = UIDevice.currentDevice.batteryState
        return state == UIDeviceBatteryState.UIDeviceBatteryStateCharging ||
                state == UIDeviceBatteryState.UIDeviceBatteryStateFull
    }

    private fun isBatteryNotLow(): Boolean {
        ensureBatteryMonitoring()
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
