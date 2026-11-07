package io.github.sahilmakkar.chronos.task.constraints

/**
 * Execution requirements that must be met before a task is dispatched.
 */
public data class EnvironmentConstraints(
    public val requiredNetworkType: NetworkType = NetworkType.NOT_REQUIRED,
    public val requiresCharging: Boolean = false,
    public val requiresBatteryNotLow: Boolean = false,
    public val requiresStorageNotLow: Boolean = false,
    @BestEffortOnIOS("Device idle is not supported on iOS targets and behaves as a no-op.")
    @BestEffortOnJVM("Device idle is not supported on JVM targets and behaves as a no-op.")
    public val requiresDeviceIdle: Boolean = false,
    public val allowedTimeWindow: TimeWindow? = null
) {

    public class Builder {
        private var requiredNetworkType: NetworkType = NetworkType.NOT_REQUIRED
        private var requiresCharging: Boolean = false
        private var requiresBatteryNotLow: Boolean = false
        private var requiresStorageNotLow: Boolean = false
        private var requiresDeviceIdle: Boolean = false
        private var allowedTimeWindow: TimeWindow? = null

        public fun setRequiredNetworkType(networkType: NetworkType): Builder =
            apply { this.requiredNetworkType = networkType }

        public fun setRequiresCharging(requiresCharging: Boolean): Builder =
            apply { this.requiresCharging = requiresCharging }

        public fun setRequiresBatteryNotLow(requiresBatteryNotLow: Boolean): Builder =
            apply { this.requiresBatteryNotLow = requiresBatteryNotLow }

        public fun setRequiresStorageNotLow(requiresStorageNotLow: Boolean): Builder =
            apply { this.requiresStorageNotLow = requiresStorageNotLow }

        public fun setRequiresDeviceIdle(requiresDeviceIdle: Boolean): Builder =
            apply { this.requiresDeviceIdle = requiresDeviceIdle }

        public fun setAllowedTimeWindow(timeWindow: TimeWindow?): Builder =
            apply { this.allowedTimeWindow = timeWindow }

        public fun build(): EnvironmentConstraints = EnvironmentConstraints(
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
public annotation class BestEffortOnIOS(public val message: String = "")

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class BestEffortOnJVM(public val message: String = "")
