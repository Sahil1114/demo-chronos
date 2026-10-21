package io.github.sahilmakkar.chronos.scheduler

import io.github.sahilmakkar.chronos.port.TaskCanceller
import io.github.sahilmakkar.chronos.storage.ChronosDatabase
import io.github.sahilmakkar.chronos.task.TaskState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class ChronosTaskCanceller(
    private val db: ChronosDatabase,
    private val coroutineScope: CoroutineScope,
    private val onCancelById: ((String) -> Unit)? = null,
    private val onCancelByTag: ((String) -> Unit)? = null,
    private val onCancelAll: (() -> Unit)? = null,
) : TaskCanceller {

    override fun cancelTaskById(id: String) {
        onCancelById?.invoke(id)
        coroutineScope.launch {
            db.taskDao().updateTaskState(id = id, state = TaskState.CANCELLED)
            cancelDownstream(id)
        }
    }

    override fun cancelTaskByTag(tag: String) {
        onCancelByTag?.invoke(tag)
        coroutineScope.launch {
            db.taskDao().getTaskIdsByTag(tag).forEach { cancelTaskById(it) }
        }
    }

    override fun cancelAllTasks() {
        onCancelAll?.invoke()
        coroutineScope.launch {
            db.taskDao().getAllTasks().forEach {
                db.taskDao().updateTaskState(id = it.id, state = TaskState.CANCELLED)
            }
        }
    }

    private suspend fun cancelDownstream(parentId: String) {
        db.taskDao().getDependenciesForParent(parentId).forEach { dep ->
            db.taskDao().updateTaskState(id = dep.childTaskId, state = TaskState.CANCELLED)
            cancelDownstream(parentId = dep.childTaskId)
        }
    }
}

@Deprecated("Use ChronosTaskCanceller instead", ReplaceWith("ChronosTaskCanceller"))
internal typealias ChronosWorkCanceller = ChronosTaskCanceller
