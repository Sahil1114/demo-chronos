package io.github.sahilmakkar.chronos.scheduler

import io.github.sahilmakkar.chronos.port.TaskCanceller
import io.github.sahilmakkar.chronos.storage.ChronosDatabase
import io.github.sahilmakkar.chronos.task.TaskState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Default implementation of [TaskCanceller] handling task cancellation and dependency pruning.
 *
 * Transitions target tasks to [TaskState.CANCELLED], cascades cancellation to all downstream
 * chain dependents, and recalibrates background wake triggers.
 */
internal class ChronosTaskCanceller(
    private val db: ChronosDatabase,
    private val coroutineScope: CoroutineScope,
    private val onCancelById: ((String) -> Unit)? = null,
    private val onCancelByTag: ((String) -> Unit)? = null,
    private val onCancelAll: (() -> Unit)? = null,
    private val wakeScheduler: WakeScheduler? = null
) : TaskCanceller {

    /**
     * Cancels an active task matching [id] and cascades cancellation downstream.
     *
     * Transitions the target task to [TaskState.CANCELLED] if it is currently in an active state
     * ([TaskState.ENQUEUED], [TaskState.RUNNING], or [TaskState.RETRYING]). If updated, recursively cancels
     * all child tasks depending on this task, then recalibrates the OS wake timer.
     *
     * @param id Canonical UUID string of the task to cancel.
     */
    override fun cancelTaskById(id: String) {
        onCancelById?.invoke(id)
        coroutineScope.launch {
            val updated = db.taskDao().cancelTaskIfActive(id)
            if (updated > 0) {
                cancelDownstream(id)
            }
            wakeScheduler?.scheduleNextWake()
        }
    }

    /**
     * Cancels all active tasks tagged with [tag] and cascades cancellation downstream.
     *
     * @param tag Tag grouping name identifying tasks to cancel.
     */
    override fun cancelTaskByTag(tag: String) {
        onCancelByTag?.invoke(tag)
        coroutineScope.launch {
            db.taskDao().getTaskIdsByTag(tag).forEach { id ->
                val updated = db.taskDao().cancelTaskIfActive(id)
                if (updated > 0) {
                    cancelDownstream(id)
                }
            }
            wakeScheduler?.scheduleNextWake()
        }
    }

    /**
     * Cancels all active tasks across the entire database.
     *
     * Transitions all tasks in [TaskState.ENQUEUED] or [TaskState.RETRYING] to [TaskState.CANCELLED]
     * and cancels any pending OS background wake alarm/worker.
     */
    override fun cancelAllTasks() {
        onCancelAll?.invoke()
        coroutineScope.launch {
            db.taskDao().cancelAllActiveTasks()
            wakeScheduler?.scheduleNextWake()
        }
    }

    /**
     * Recursively cancels all downstream dependent tasks of [parentId].
     */
    private suspend fun cancelDownstream(parentId: String) {
        db.taskDao().getDependenciesForParent(parentId).forEach { dep ->
            val updated = db.taskDao().cancelTaskIfActive(dep.childTaskId)
            if (updated > 0) {
                cancelDownstream(parentId = dep.childTaskId)
            }
        }
    }
}
