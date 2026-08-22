package io.github.sahilmakkar.chronos.task

/**
 * Represents the lifecycle state of a scheduled [io.github.sahilmakkar.chronos.task.request.TaskRequest].
 */
enum class TaskState {
    /**
     * Task is enqueued and waiting for trigger conditions (initial delay, dependencies, constraints) to be met.
     */
    ENQUEUED,

    /**
     * Task is currently running on the execution thread.
     */
    RUNNING,

    /**
     * Task completed successfully and is marked as succeeded.
     */
    SUCCEEDED,

    /**
     * Task failed and cannot be retried.
     */
    FAILED,

    /**
     * Task failed but is scheduled to retry in the future (respecting backoff policy).
     */
    RETRYING,

    /**
     * Task was cancelled either by ID, tag, or chain cascading cancellation.
     */
    CANCELLED
}