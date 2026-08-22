package io.github.sahilmakkar.chronos.task.policy

/**
 * Defines what happens to downstream dependent tasks in a chain when a parent task fails.
 */
enum class DependentTaskPolicy {
    /**
     * Halts execution and marks all remaining dependent tasks in the chain as FAILED.
     */
    STOP_ON_FAILURE,

    /**
     * Continues execution of downstream tasks even if the parent task fails.
     */
    CONTINUE_ON_FAILURE
}