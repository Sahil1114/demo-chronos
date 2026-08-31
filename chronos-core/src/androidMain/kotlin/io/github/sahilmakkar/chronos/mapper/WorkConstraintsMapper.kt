package io.github.sahilmakkar.chronos.mapper

import androidx.work.Constraints
import io.github.sahilmakkar.chronos.task.policy.BackoffPolicy
import io.github.sahilmakkar.chronos.task.constraints.EnvironmentConstraints
import io.github.sahilmakkar.chronos.network.NetworkType

internal fun BackoffPolicy.toNative(): androidx.work.BackoffPolicy = when (this) {
    BackoffPolicy.LINEAR -> androidx.work.BackoffPolicy.LINEAR
    BackoffPolicy.EXPONENTIAL -> androidx.work.BackoffPolicy.EXPONENTIAL
}

private fun NetworkType.toNativeNetworkType(): androidx.work.NetworkType = when (this) {
    NetworkType.NOT_REQUIRED -> androidx.work.NetworkType.NOT_REQUIRED
    NetworkType.CONNECTED -> androidx.work.NetworkType.CONNECTED
    NetworkType.UNMETERED -> androidx.work.NetworkType.UNMETERED
  }
internal object WorkConstraintsMapper {
    fun toConstraints(constraints: EnvironmentConstraints): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(constraints.requiredNetworkType.toNativeNetworkType())
            .setRequiresCharging(constraints.requiresCharging)
            .setRequiresBatteryNotLow(constraints.requiresBatteryNotLow)
            .setRequiresStorageNotLow(constraints.requiresStorageNotLow)
            .setRequiresDeviceIdle(constraints.requiresDeviceIdle)
            .build()
}