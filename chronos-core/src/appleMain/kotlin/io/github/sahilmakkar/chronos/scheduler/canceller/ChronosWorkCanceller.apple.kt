package io.github.sahilmakkar.chronos.scheduler.canceller

import io.github.sahilmakkar.chronos.db.WorkDatabase
import io.github.sahilmakkar.chronos.task.TaskState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


actual class ChronosWorkCanceller(
    private val db: WorkDatabase,
    private val scope: CoroutineScope
) : WorkCanceller {

    actual override fun cancelWorkById(id: String) {
        scope.launch { cancelByIdInternal(id) }
    }

    actual override fun cancelWorkByTag(tag: String) {
        scope.launch {
            db.workDao().getWorkRequestIdsByTag(tag).forEach { cancelByIdInternal(it) }
        }
    }

    actual override fun cancelAllWork() {
        scope.launch {
            db.workDao().getAllWorkRequests().forEach {
                db.workDao().updateWorkState(id = it.id, state = TaskState.CANCELLED)
            }
        }
    }

    private suspend fun cancelByIdInternal(id: String) {
        db.workDao().updateWorkState(id = id, state = TaskState.CANCELLED)
        cancelDownstream(id)
    }

    private suspend fun cancelDownstream(parentId: String) {
        db.workDao().getDependenciesForParent(parentId).forEach { dep ->
            db.workDao().updateWorkState(id = dep.childWorkId, state = TaskState.CANCELLED)
            cancelDownstream(dep.childWorkId)
        }
    }

}