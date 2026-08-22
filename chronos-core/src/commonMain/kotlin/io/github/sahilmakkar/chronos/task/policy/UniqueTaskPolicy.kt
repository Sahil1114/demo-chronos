package io.github.sahilmakkar.chronos.task.policy

/**
 * Policy defining behavior when enqueuing unique task names that already exist.
 */
enum class UniqueTaskPolicy {
    /**
     * Keep the existing active task and ignore the new request.
     */
    KEEP,

    /**
     * Cancel and delete the existing task and replace it with the new request.
     */
    REPLACE,

    /**
     * Appends the new task to the end of the existing active task's chain.
     */
    APPEND
}