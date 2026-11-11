package io.github.sahilmakkar.chronos.executor

import io.github.sahilmakkar.chronos.task.request.TaskRequest
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import platform.BackgroundTasks.*
import platform.Foundation.*

/**
 * iOS integration bridge connecting Chronos with Apple's `BGTaskScheduler` framework.
 *
 * Registers the background app refresh task identifier (`io.github.sahilmakkar.chronos.bg_task`)
 * and handles background wake dispatches from iOS. Also handles expiration callbacks by triggering
 * orphan sweeps if iOS cuts background runtime short.
 */
internal class IosBackgroundTaskScheduler(
    private val coroutineScope: CoroutineScope,
    private val onTaskFired: suspend () -> Unit,
    private val onExpired: suspend () -> Unit
) {
    private val bgTaskId = "io.github.sahilmakkar.chronos.bg_task"

    /**
     * Registers the background refresh task handler with iOS [BGTaskScheduler].
     *
     * Must be called during application launch before the end of `didFinishLaunchingWithOptions`.
     */
    fun register() {
        if (NSBundle.mainBundle.bundleIdentifier == null) return
        BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(
            bgTaskId,
            usingQueue = null
        ) { task ->
            (task as? BGAppRefreshTask)?.let { handleBgTask(it) }
        }
    }

    /**
     * Cancels any pending iOS background app refresh task request.
     */
    fun cancelScheduled() {
        BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(bgTaskId)
    }

    /**
     * Submits an iOS background task request scheduled to wake after [request]'s initial delay.
     *
     * @param request The task request whose delay determines the earliest begin date.
     */
    fun scheduleForRequest(request: TaskRequest) {
        if (NSBundle.mainBundle.bundleIdentifier == null) return
        val delaySec = request.initialDelay.inWholeSeconds.toDouble()
        submitRequest(if (delaySec > 0) delaySec else DEFAULT_REFRESH_INTERVAL)
    }

    /**
     * Reschedules the next periodic background refresh cycle.
     */
    private fun scheduleNextRefresh() {
        if (NSBundle.mainBundle.bundleIdentifier == null) return
        submitRequest(DEFAULT_REFRESH_INTERVAL)
    }

    /**
     * Submits a [BGAppRefreshTaskRequest] to the system with [earliestBeginDate] offset by [intervalSecs].
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun submitRequest(intervalSecs: Double) {
        BGAppRefreshTaskRequest(bgTaskId).also {
            it.earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(intervalSecs)
            BGTaskScheduler.sharedScheduler.submitTaskRequest(it, null)
        }
    }

    /**
     * Handles an incoming background task dispatch from iOS.
     *
     * Dispatches ready tasks, re-registers the next wake cycle, and wires up iOS's expiration handler
     * to perform orphan sweeps if execution time is terminated by the OS.
     */
    private fun handleBgTask(task: BGAppRefreshTask) {
        scheduleNextRefresh()
        val job = coroutineScope.launch { onTaskFired() }
        task.expirationHandler = {
            job.cancel()
            coroutineScope.launch {
                onExpired()
                task.setTaskCompletedWithSuccess(false)
            }
        }
        job.invokeOnCompletion { task.setTaskCompletedWithSuccess(true) }
    }

    companion object {
        private const val DEFAULT_REFRESH_INTERVAL = 15 * 60.0
    }
}