package io.github.sahilmakkar.chronos.constraints

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import io.github.sahilmakkar.chronos.AndroidNetworkChecker
import io.github.sahilmakkar.chronos.port.NetworkChecker
import io.github.sahilmakkar.chronos.storage.TaskEntity
import kotlin.time.Clock

/**
 * Android implementation of [ConstraintVerifier] evaluating device battery, storage, and network state.
 *
 * Interrogates Android framework system services:
 * - [android.net.ConnectivityManager] via [AndroidNetworkChecker] for active and unmetered network links.
 * - [BatteryManager] and sticky [Intent.ACTION_BATTERY_CHANGED] broadcasts for charging status and capacity.
 *
 * @param context Android application context used to query system managers.
 * @param networkChecker Network connectivity verifier. Defaults to [AndroidNetworkChecker].
 * @param clock Time provider for evaluating time windows. Defaults to [Clock.System].
 */
internal class AndroidConstraintVerifier(
    private val context: Context,
    networkChecker: NetworkChecker = AndroidNetworkChecker(context),
    clock: Clock = Clock.System
) : BaseConstraintVerifier(networkChecker = networkChecker, clock = clock) {

    /**
     * Checks if all constraints defined on [entity] are met on Android.
     *
     * @param entity The task record containing constraints to check.
     * @return `true` if all constraints pass; `false` otherwise.
     */
    override fun isSatisfied(entity: TaskEntity): Boolean {
        if (!verifyTimeWindow(entity)) return false
        if (!verifyNetwork(entity)) return false
        if (entity.requiresCharging && !isCharging()) return false
        if (entity.requiresBatteryNotLow && !isBatteryNotLow()) return false
        return true
    }

    /**
     * Queries battery status to check whether the device is currently plugged into AC/USB/wireless power.
     *
     * Uses [BatteryManager.isCharging] on API 23+, falling back to sticky battery broadcast inspection.
     *
     * @return `true` if charging or full; `false` otherwise.
     */
    private fun isCharging(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            if (batteryManager?.isCharging == true) return true
        }
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    /**
     * Checks whether remaining battery capacity is above the low-battery threshold (> 15%).
     *
     * Queries [BatteryManager.EXTRA_BATTERY_LOW] on API 28+, calculates percentage from level/scale,
     * or queries [BatteryManager.BATTERY_PROPERTY_CAPACITY].
     *
     * @return `true` if battery level exceeds 15%; `false` otherwise.
     */
    private fun isBatteryNotLow(): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val isLow = intent?.getBooleanExtra(BatteryManager.EXTRA_BATTERY_LOW, false) ?: false
            if (isLow) return false
        }
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level >= 0 && scale > 0) {
            val pct = (level * 100) / scale
            return pct > 15
        }
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val capacity = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        return capacity > 15
    }
}
