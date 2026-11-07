package io.github.sahilmakkar.chronos.task.constraints

/**
 * Execution requirements that must be met before a task is dispatched.
 *
 * @property requiredNetworkType Type of network connectivity required for task execution.
 * @property requiresCharging Whether the device must be plugged in and charging to run the task.
 * @property requiresBatteryNotLow Whether the device battery level must not be in a low battery state.
 * @property requiresStorageNotLow Whether available device storage must not be below a critical threshold.
 * @property requiresDeviceIdle Whether the device must be idle. Best-effort on supported platforms.
 * @property allowedTimeWindow Optional time-of-day window outside of which the task will not execute.
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

    /**
     * Builder for constructing [EnvironmentConstraints] instances.
     */
    public class Builder {
        private var requiredNetworkType: NetworkType = NetworkType.NOT_REQUIRED
        private var requiresCharging: Boolean = false
        private var requiresBatteryNotLow: Boolean = false
        private var requiresStorageNotLow: Boolean = false
        private var requiresDeviceIdle: Boolean = false
        private var allowedTimeWindow: TimeWindow? = null

        /**
         * Sets the required network type constraint.
         */
        public fun setRequiredNetworkType(networkType: NetworkType): Builder =
            apply { this.requiredNetworkType = networkType }

        /**
         * Sets whether the device must be actively charging to execute the task.
         */
        public fun setRequiresCharging(requiresCharging: Boolean): Builder =
            apply { this.requiresCharging = requiresCharging }

        /**
         * Sets whether the device must have sufficient battery (not low) to execute the task.
         */
        public fun setRequiresBatteryNotLow(requiresBatteryNotLow: Boolean): Builder =
            apply { this.requiresBatteryNotLow = requiresBatteryNotLow }

        /**
         * Sets whether device storage must not be critically low to execute the task.
         */
        public fun setRequiresStorageNotLow(requiresStorageNotLow: Boolean): Builder =
            apply { this.requiresStorageNotLow = requiresStorageNotLow }

        /**
         * Sets whether the device must be idle to execute the task.
         */
        public fun setRequiresDeviceIdle(requiresDeviceIdle: Boolean): Builder =
            apply { this.requiresDeviceIdle = requiresDeviceIdle }

        /**
         * Restricts execution to the specified time of day window.
         */
        public fun setAllowedTimeWindow(timeWindow: TimeWindow?): Builder =
            apply { this.allowedTimeWindow = timeWindow }

        /**
         * Constructs the immutable [EnvironmentConstraints] configured by this builder.
         */
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

/**
 * Documents an API or constraint feature that operates on a best-effort basis on iOS targets.
 *
 * @property message Informational explanation regarding iOS limitations or fallback behavior.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class BestEffortOnIOS(public val message: String = "")

/**
 * Documents an API or constraint feature that operates on a best-effort basis on JVM targets.
 *
 * @property message Informational explanation regarding JVM limitations or fallback behavior.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class BestEffortOnJVM(public val message: String = "")
