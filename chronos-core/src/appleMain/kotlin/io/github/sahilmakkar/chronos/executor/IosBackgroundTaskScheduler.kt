package io.github.sahilmakkar.chronos.executor

import io.github.sahilmakkar.chronos.task.request.TaskRequest
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import platform.BackgroundTasks.*
import platform.Foundation.*

class IosBackgroundTaskScheduler(
    private val coroutineScope: CoroutineScope,
    private val onTaskFired: suspend () -> Unit,
    private val onExpired: suspend () -> Unit
) {
    private val bgTaskId = "io.github.sahilmakkar.chronos.bg_task"

    fun register() {
        if (NSBundle.mainBundle.bundleIdentifier == null) return
        BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(
            bgTaskId,
            usingQueue = null
        ) { task ->
            (task as? BGAppRefreshTask)?.let { handleBgTask(it) }
        }
    }

    fun scheduleForRequest(request: TaskRequest) {
        if (NSBundle.mainBundle.bundleIdentifier == null) return
        val delaySec = request.initialDelay.inWholeSeconds.toDouble()
        submitRequest(if (delaySec > 0) delaySec else DEFAULT_REFRESH_INTERVAL)
    }

    private fun scheduleNextRefresh() {
        if (NSBundle.mainBundle.bundleIdentifier == null) return
        submitRequest(DEFAULT_REFRESH_INTERVAL)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun submitRequest(intervalSecs: Double) {
        BGAppRefreshTaskRequest(bgTaskId).also {
            it.earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(intervalSecs)
            BGTaskScheduler.sharedScheduler.submitTaskRequest(it, null)
        }
    }

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