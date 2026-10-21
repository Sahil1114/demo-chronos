package io.github.sahilmakkar.chronos.task.constraints

data class EnvironmentConstraints(
    val requiredNetworkType: NetworkType = NetworkType.NOT_REQUIRED,
    val requiresCharging: Boolean = false,
    val requiresBatteryNotLow: Boolean = false,
    val requiresStorageNotLow: Boolean = false,
    @BestEffortOnIOS("Device idle is not supported on iOS targets and behaves as a no-op.")
    @BestEffortOnJVM("Device idle is not supported on JVM targets and behaves as a no-op.")
    val requiresDeviceIdle: Boolean = false,
    val allowedTimeWindow: TimeWindow? = null
) {

    class Builder {
        private var requiredNetworkType: NetworkType = NetworkType.NOT_REQUIRED
        private var requiresCharging: Boolean = false
        private var requiresBatteryNotLow: Boolean = false
        private var requiresStorageNotLow: Boolean = false
        private var requiresDeviceIdle: Boolean = false
        private var allowedTimeWindow: TimeWindow? = null

        fun setRequiredNetworkType(networkType: NetworkType) =
            apply { this.requiredNetworkType = networkType }

        fun setRequiresCharging(requiresCharging: Boolean) =
            apply { this.requiresCharging = requiresCharging }

        fun setRequiresBatteryNotLow(requiresBatteryNotLow: Boolean) =
            apply { this.requiresBatteryNotLow = requiresBatteryNotLow }

        fun setRequiresStorageNotLow(requiresStorageNotLow: Boolean) =
            apply { this.requiresStorageNotLow = requiresStorageNotLow }

        fun setRequiresDeviceIdle(requiresDeviceIdle: Boolean) =
            apply { this.requiresDeviceIdle = requiresDeviceIdle }

        fun setAllowedTimeWindow(timeWindow: TimeWindow?) =
            apply { this.allowedTimeWindow = timeWindow }

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

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class BestEffortOnJVM(val message: String = "")