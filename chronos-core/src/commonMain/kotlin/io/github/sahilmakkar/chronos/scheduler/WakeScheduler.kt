package io.github.sahilmakkar.chronos.scheduler

/**
 * Common port interface for scheduling platform OS background wake triggers.
 *
 * Used by Android (via WorkManager) and iOS (via BGTaskScheduler) to wake the application
 * process when scheduled background tasks become due.
 *
 * Chronos decouples execution from OS schedulers: the OS scheduler is only responsible for
 * waking the host process, at which point the in-process [io.github.sahilmakkar.chronos.engine.TaskCoordinator]
 * takes over execution with high concurrency and SQLite state persistence.
 */
internal interface WakeScheduler {

    /**
     * Inspects the database for the earliest pending task in [io.github.sahilmakkar.chronos.task.TaskState.ENQUEUED]
     * or [io.github.sahilmakkar.chronos.task.TaskState.RETRYING] states and schedules an OS background wake trigger.
     *
     * Calculates delay as `maxOf(0L, nextDueAt - now)`, maps hardware/network constraints to the OS scheduler,
     * and registers the wake trigger. If no tasks remain pending, cancels any pending wake.
     */
    suspend fun scheduleNextWake()

    /**
     * Cancels any pending OS background wake trigger registered by Chronos.
     *
     * Called when all pending tasks are completed or cancelled, or when Chronos is shutting down.
     */
    fun cancelWake()
}
