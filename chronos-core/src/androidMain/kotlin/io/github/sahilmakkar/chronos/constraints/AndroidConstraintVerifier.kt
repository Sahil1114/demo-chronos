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
 */
internal class AndroidConstraintVerifier(
    private val context: Context,
    networkChecker: NetworkChecker = AndroidNetworkChecker(context),
    clock: Clock = Clock.System
) : BaseConstraintVerifier(networkChecker = networkChecker, clock = clock) {

    override fun isSatisfied(entity: TaskEntity): Boolean {
        if (!verifyTimeWindow(entity)) return false
        if (!verifyNetwork(entity)) return false
        if (entity.requiresCharging && !isCharging()) return false
        if (entity.requiresBatteryNotLow && !isBatteryNotLow()) return false
        return true
    }

    private fun isCharging(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            if (batteryManager?.isCharging == true) return true
        }
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

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
