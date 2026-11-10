package io.github.sahilmakkar.chronos.task.policy

/**
 * Conflict resolution policy applied when enqueuing a task with a unique name identifier.
 *
 * Unique tasks prevent duplicate tasks from running or queuing concurrently (e.g. background syncs
 * or database cleanups).
 *
 * @see io.github.sahilmakkar.chronos.Chronos.enqueueTask
 * @see io.github.sahilmakkar.chronos.Chronos.enqueuePeriodicTask
 */
public enum class UniqueTaskPolicy {
    /**
     * Preserves the existing active task and drops the newly submitted task request.
     *
     * If an active task with the same unique name exists (in `ENQUEUED`, `RUNNING`, or `RETRYING` states),
     * the new request is ignored. If no active task with the name exists, the new request is enqueued.
     */
    KEEP,

    /**
     * Cancels and deletes the existing active task and replaces it with the new task request.
     *
     * The existing task is cancelled, any pending execution passes are aborted, and the new request
     * is enqueued with the unique name.
     */
    REPLACE,

    /**
     * Appends the new task to the end of the existing active task (or task chain).
     *
     * Locates the leaf task of the existing unique chain and attaches the new request as a child
     * dependent task, ensuring sequential execution after the current chain completes.
     *
     * Note: Not supported for periodic tasks.
     */
    APPEND
}
