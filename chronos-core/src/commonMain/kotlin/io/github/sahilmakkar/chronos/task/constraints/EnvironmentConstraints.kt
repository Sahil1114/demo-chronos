package io.github.sahilmakkar.chronos.task.constraints

/**
 * Execution prerequisites that must be satisfied by the host device before a task is eligible for dispatch.
 *
 * If any specified constraint is not satisfied when a task reaches its scheduled execution time, the task
 * remains in the [io.github.sahilmakkar.chronos.task.TaskState.ENQUEUED] state. Chronos evaluates constraints
 * dynamically via platform listeners and the background daemon loop, dispatching the task immediately once
 * all prerequisites are fulfilled.
 *
 * ### Platform Support
 * Certain constraints (such as [requiresDeviceIdle]) are only natively supported on Android. For unsupported
 * platforms, the constraint operates on a best-effort basis (behaving as satisfied). Such constraints are annotated
 * with [BestEffortOnIOS] or [BestEffortOnJVM].
 *
 * ### Usage Example
 * ```kotlin
 * val constraints = EnvironmentConstraints.Builder()
 *     .setRequiredNetworkType(NetworkType.UNMETERED)
 *     .setRequiresCharging(true)
 *     .setRequiresBatteryNotLow(true)
 *     .setAllowedTimeWindow(TimeWindow(startHour = 2, endHour = 6, timeZone = "UTC"))
 *     .build()
 * ```
 *
 * @property requiredNetworkType Type of network connectivity required for task execution.
 * @property requiresCharging Whether the device must be plugged into external power and charging.
 * @property requiresBatteryNotLow Whether the device battery level must not be in a low battery state (> 15-20%).
 * @property requiresStorageNotLow Whether available device storage must not be critically low (> 100MB).
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
     * Fluent builder for assembling immutable [EnvironmentConstraints] configurations.
     */
    public class Builder {
        private var requiredNetworkType: NetworkType = NetworkType.NOT_REQUIRED
        private var requiresCharging: Boolean = false
        private var requiresBatteryNotLow: Boolean = false
        private var requiresStorageNotLow: Boolean = false
        private var requiresDeviceIdle: Boolean = false
        private var allowedTimeWindow: TimeWindow? = null

        /**
         * Sets the required network connectivity constraint.
         *
         * @param networkType Required network type (e.g. [NetworkType.CONNECTED] or [NetworkType.UNMETERED]).
         * @return This builder instance for chaining calls.
         */
        public fun setRequiredNetworkType(networkType: NetworkType): Builder =
            apply { this.requiredNetworkType = networkType }

        /**
         * Sets whether the device must be actively connected to external charging power.
         *
         * @param requiresCharging `true` if external power is required; `false` otherwise.
         * @return This builder instance for chaining calls.
         */
        public fun setRequiresCharging(requiresCharging: Boolean): Builder =
            apply { this.requiresCharging = requiresCharging }

        /**
         * Sets whether the device battery level must be above the low-battery threshold (> 15-20%).
         *
         * @param requiresBatteryNotLow `true` to defer execution when battery is low; `false` otherwise.
         * @return This builder instance for chaining calls.
         */
        public fun setRequiresBatteryNotLow(requiresBatteryNotLow: Boolean): Builder =
            apply { this.requiresBatteryNotLow = requiresBatteryNotLow }

        /**
         * Sets whether host device storage must have adequate free disk space (> 100MB).
         *
         * @param requiresStorageNotLow `true` to prevent execution on critically full disks; `false` otherwise.
         * @return This builder instance for chaining calls.
         */
        public fun setRequiresStorageNotLow(requiresStorageNotLow: Boolean): Builder =
            apply { this.requiresStorageNotLow = requiresStorageNotLow }

        /**
         * Sets whether the device must be idle before dispatching the task.
         *
         * @param requiresDeviceIdle `true` if device idle state is required; `false` otherwise.
         * @return This builder instance for chaining calls.
         */
        public fun setRequiresDeviceIdle(requiresDeviceIdle: Boolean): Builder =
            apply { this.requiresDeviceIdle = requiresDeviceIdle }

        /**
         * Restricts execution to the specified daily time-of-day window.
         *
         * @param timeWindow Allowed daily execution window, or `null` for unrestricted 24/7 execution.
         * @return This builder instance for chaining calls.
         */
        public fun setAllowedTimeWindow(timeWindow: TimeWindow?): Builder =
            apply { this.allowedTimeWindow = timeWindow }

        /**
         * Constructs the immutable [EnvironmentConstraints] configured by this builder.
         *
         * @return An immutable [EnvironmentConstraints] instance.
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
