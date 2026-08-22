package io.github.sahilmakkar.chronos.task.constraints

import io.github.sahilmakkar.chronos.network.NetworkType

/**
 * Declares conditions/requirements that must be met for a task to execute.
 *
 * @property requiredNetworkType Required network connection state.
 * @property requiresCharging Whether the device must be plugged into power.
 * @property requiresBatteryNotLow Whether the device battery level must not be critically low.
 * @property requiresStorageNotLow Whether the device storage must not be critically low.
 * @property requiresDeviceIdle Whether the device must be idle (supported on Android).
 * @property allowedTimeWindow Optional daily window restriction for execution.
 */
data class EnvironmentConstraints(
    val requiredNetworkType: NetworkType = NetworkType.NOT_REQUIRED,
    val requiresCharging: Boolean = false,
    val requiresBatteryNotLow: Boolean = false,
    val requiresStorageNotLow: Boolean = false,
    @BestEffortOnIOS("Device idle is not supported on iOS/JVM targets and behaves as a no-op.")
    val requiresDeviceIdle: Boolean = false,
    val allowedTimeWindow: TimeWindow? = null
) {
    /**
     * Builder to construct [EnvironmentConstraints].
     */
    class Builder {
        private var requiredNetworkType: NetworkType = NetworkType.NOT_REQUIRED
        private var requiresCharging: Boolean = false
        private var requiresBatteryNotLow: Boolean = false
        private var requiresStorageNotLow: Boolean = false
        private var requiresDeviceIdle: Boolean = false
        private var allowedTimeWindow: TimeWindow? = null

        /**
         * Sets the required [NetworkType].
         */
        fun setRequiredNetworkType(networkType: NetworkType) =
            apply { this.requiredNetworkType = networkType }

        /**
         * Sets whether charging is required.
         */
        fun setRequiresCharging(requiresCharging: Boolean) =
            apply { this.requiresCharging = requiresCharging }

        /**
         * Sets whether execution requires battery not low.
         */
        fun setRequiresBatteryNotLow(requiresBatteryNotLow: Boolean) =
            apply { this.requiresBatteryNotLow = requiresBatteryNotLow }

        /**
         * Sets whether execution requires storage not low.
         */
        fun setRequiresStorageNotLow(requiresStorageNotLow: Boolean) =
            apply { this.requiresStorageNotLow = requiresStorageNotLow }

        /**
         * Sets whether execution requires device idle state.
         */
        fun setRequiresDeviceIdle(requiresDeviceIdle: Boolean) =
            apply { this.requiresDeviceIdle = requiresDeviceIdle }

        /**
         * Sets an optional allowed [TimeWindow] for execution.
         */
        fun setAllowedTimeWindow(timeWindow: TimeWindow?) =
            apply { this.allowedTimeWindow = timeWindow }

        /**
         * Builds the [EnvironmentConstraints] instance.
         */
        fun build() = EnvironmentConstraints(
            requiredNetworkType = requiredNetworkType,
            requiresCharging = requiresCharging,
            requiresBatteryNotLow = requiresBatteryNotLow,
            requiresStorageNotLow = requiresStorageNotLow,
            requiresDeviceIdle = requiresDeviceIdle,
            allowedTimeWindow = allowedTimeWindow
        )
    }
}




@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class BestEffortOnIOS(val message: String = "")