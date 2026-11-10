package io.github.sahilmakkar.chronos.task.policy

/**
 * Policy defining how failure of a parent task in an execution chain affects downstream dependent tasks.
 *
 * Configured per dependency edge when appending tasks to a [io.github.sahilmakkar.chronos.task.TaskChain].
 *
 * @see io.github.sahilmakkar.chronos.task.TaskChain.then
 */
public enum class DependentTaskPolicy {
    /**
     * Halts downstream execution immediately upon parent failure.
     *
     * All remaining downstream tasks in the chain are transitioned directly to
     * [io.github.sahilmakkar.chronos.task.TaskState.FAILED] without executing.
     * This is the default policy for chained tasks.
     */
    STOP_ON_FAILURE,

    /**
     * Continues downstream execution regardless of parent failure.
     *
     * The child task is scheduled to run once the parent reaches [io.github.sahilmakkar.chronos.task.TaskState.FAILED].
     * Useful for cleanup, error reporting, or telemetry upload steps that must run even if earlier steps failed.
     */
    CONTINUE_ON_FAILURE
}
