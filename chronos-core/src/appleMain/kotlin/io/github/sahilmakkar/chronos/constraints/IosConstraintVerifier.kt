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
 * Apple/iOS implementation of [ConstraintVerifier] evaluating device state and network connectivity.
 *
 * Utilizes Apple frameworks:
 * - [UIDevice] for battery level, battery state, and power source monitoring.
 * - [NSFileManager] filesystem attributes for checking available sandbox disk space (> 100MB).
 * - [NetworkChecker] (via Network framework `nw_path_monitor_t`) for network connectivity and cost.
 *
 * @param networkChecker Network connectivity verifier.
 * @param clock Time provider for evaluating time windows. Defaults to [Clock.System].
 */
internal class IosConstraintVerifier(
    networkChecker: NetworkChecker,
    clock: Clock = Clock.System
) : BaseConstraintVerifier(networkChecker = networkChecker, clock = clock) {

    /**
     * Ensures battery monitoring is activated on [UIDevice.currentDevice].
     */
    private fun ensureBatteryMonitoring() {
        if (!UIDevice.currentDevice.batteryMonitoringEnabled) {
            UIDevice.currentDevice.batteryMonitoringEnabled = true
        }
    }

    /**
     * Checks if all constraints defined on [entity] are met on iOS.
     *
     * @param entity The task record containing constraints to verify.
     * @return `true` if all constraints pass; `false` otherwise.
     */
    override fun isSatisfied(entity: TaskEntity): Boolean {
        if (!verifyNetwork(entity = entity)) return false
        if (entity.requiresCharging && !isCharging()) return false
        if (entity.requiresBatteryNotLow && !isBatteryNotLow()) return false
        if (entity.requiresStorageNotLow && !isStorageNotLow()) return false
        if (!verifyTimeWindow(entity = entity)) return false
        return true
    }

    /**
     * Queries [UIDevice.batteryState] to determine if the iOS device is actively plugged into external power.
     *
     * @return `true` if charging or full; `false` otherwise.
     */
    private fun isCharging(): Boolean {
        ensureBatteryMonitoring()
        val state = UIDevice.currentDevice.batteryState
        return state == UIDeviceBatteryState.UIDeviceBatteryStateCharging ||
                state == UIDeviceBatteryState.UIDeviceBatteryStateFull
    }

    /**
     * Verifies whether iOS battery level is not low (>= 20%).
     *
     * @return `true` if battery level is 20% or higher, or unknown; `false` if below 20%.
     */
    private fun isBatteryNotLow(): Boolean {
        ensureBatteryMonitoring()
        val level = UIDevice.currentDevice.batteryLevel
        return level !in 0.0f..<0.20f
    }

    /**
     * Checks whether available filesystem disk space in the sandbox home directory exceeds 100MB.
     *
     * @return `true` if free disk space exceeds 100MB; `false` otherwise.
     */
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
