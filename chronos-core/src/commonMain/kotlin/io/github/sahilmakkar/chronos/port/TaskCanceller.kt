package io.github.sahilmakkar.chronos.port

/**
 * Internal architecture port defining task cancellation capabilities.
 *
 * Coordinates the cancellation of active, enqueued, and retrying tasks across the underlying
 * persistence store, cascading cancellation to all dependent downstream tasks in chains,
 * and recalibrating operating system background wake timers.
 */
internal interface TaskCanceller {

    /**
     * Cancels an individual task identified by its unique [id].
     *
     * If the task is currently active ([io.github.sahilmakkar.chronos.task.TaskState.ENQUEUED],
     * [io.github.sahilmakkar.chronos.task.TaskState.RUNNING], or [io.github.sahilmakkar.chronos.task.TaskState.RETRYING]),
     * it transitions immediately to [io.github.sahilmakkar.chronos.task.TaskState.CANCELLED].
     * All direct and indirect downstream chain dependencies are recursively transitioned to
     * [io.github.sahilmakkar.chronos.task.TaskState.CANCELLED]. Terminal tasks ([io.github.sahilmakkar.chronos.task.TaskState.SUCCEEDED],
     * [io.github.sahilmakkar.chronos.task.TaskState.FAILED], or already `CANCELLED`) remain unchanged.
     *
     * @param id The canonical UUID string of the task to cancel.
     */
    fun cancelTaskById(id: String)

    /**
     * Cancels all active tasks that have been tagged with the given [tag].
     *
     * Finds every matching active task, marks it as [io.github.sahilmakkar.chronos.task.TaskState.CANCELLED],
     * cascades cancellation to dependent chain downstream children, and recalibrates wake timers.
     *
     * @param tag The tag string associated with one or more tasks.
     */
    fun cancelTaskByTag(tag: String)

    /**
     * Cancels all active tasks currently tracked by the Chronos database.
     *
     * Any task in [io.github.sahilmakkar.chronos.task.TaskState.ENQUEUED] or
     * [io.github.sahilmakkar.chronos.task.TaskState.RETRYING] is transitioned to
     * [io.github.sahilmakkar.chronos.task.TaskState.CANCELLED]. Any pending platform wake triggers
     * are cancelled.
     */
    fun cancelAllTasks()
}
