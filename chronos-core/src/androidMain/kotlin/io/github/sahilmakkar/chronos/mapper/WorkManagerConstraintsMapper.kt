package io.github.sahilmakkar.chronos.mapper

import androidx.work.Constraints
import io.github.sahilmakkar.chronos.storage.TaskEntity
import io.github.sahilmakkar.chronos.task.constraints.EnvironmentConstraints
import io.github.sahilmakkar.chronos.task.constraints.NetworkType
import io.github.sahilmakkar.chronos.task.policy.BackoffPolicy

internal fun BackoffPolicy.toNative(): androidx.work.BackoffPolicy = when (this) {
    BackoffPolicy.LINEAR -> androidx.work.BackoffPolicy.LINEAR
    BackoffPolicy.EXPONENTIAL -> androidx.work.BackoffPolicy.EXPONENTIAL
}

private fun NetworkType.toNativeNetworkType(): androidx.work.NetworkType = when (this) {
    NetworkType.NOT_REQUIRED -> androidx.work.NetworkType.NOT_REQUIRED
    NetworkType.CONNECTED -> androidx.work.NetworkType.CONNECTED
    NetworkType.UNMETERED -> androidx.work.NetworkType.UNMETERED
}

internal object WorkManagerConstraintsMapper {
    fun EnvironmentConstraints.toConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(requiredNetworkType.toNativeNetworkType())
            .setRequiresCharging(requiresCharging)
            .setRequiresBatteryNotLow(requiresBatteryNotLow)
            .setRequiresStorageNotLow(requiresStorageNotLow)
            .setRequiresDeviceIdle(requiresDeviceIdle)
            .build()

    fun TaskEntity.toWorkManagerConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(networkType.toAndroidNetworkType())
            .setRequiresCharging(requiresCharging)
            .setRequiresBatteryNotLow(requiresBatteryNotLow)
            .setRequiresStorageNotLow(requiresStorageNotLow)
            .setRequiresDeviceIdle(requiresDeviceIdle)
            .build()

    private fun NetworkType.toAndroidNetworkType(): androidx.work.NetworkType = when (this) {
        NetworkType.NOT_REQUIRED -> androidx.work.NetworkType.NOT_REQUIRED
        NetworkType.CONNECTED -> androidx.work.NetworkType.CONNECTED
        NetworkType.UNMETERED -> androidx.work.NetworkType.UNMETERED
    }
}

@Deprecated("Use WorkManagerConstraintsMapper instead", ReplaceWith("WorkManagerConstraintsMapper"))
internal typealias WorkConstraintsMapper = WorkManagerConstraintsMapper
