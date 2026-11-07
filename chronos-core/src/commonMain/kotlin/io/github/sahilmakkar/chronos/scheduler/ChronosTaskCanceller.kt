package io.github.sahilmakkar.chronos.scheduler

import io.github.sahilmakkar.chronos.port.TaskCanceller
import io.github.sahilmakkar.chronos.storage.ChronosDatabase
import io.github.sahilmakkar.chronos.task.TaskState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Default implementation of [TaskCanceller] cancelling tasks in Room database.
 */
internal class ChronosTaskCanceller(
    private val db: ChronosDatabase,
    private val coroutineScope: CoroutineScope,
    private val onCancelById: ((String) -> Unit)? = null,
    private val onCancelByTag: ((String) -> Unit)? = null,
    private val onCancelAll: (() -> Unit)? = null,
    private val wakeScheduler: WakeScheduler? = null
) : TaskCanceller {

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

    override fun cancelAllTasks() {
        onCancelAll?.invoke()
        coroutineScope.launch {
            db.taskDao().cancelAllActiveTasks()
            wakeScheduler?.scheduleNextWake()
        }
    }

    private suspend fun cancelDownstream(parentId: String) {
        db.taskDao().getDependenciesForParent(parentId).forEach { dep ->
            val updated = db.taskDao().cancelTaskIfActive(dep.childTaskId)
            if (updated > 0) {
                cancelDownstream(parentId = dep.childTaskId)
            }
        }
    }
}
