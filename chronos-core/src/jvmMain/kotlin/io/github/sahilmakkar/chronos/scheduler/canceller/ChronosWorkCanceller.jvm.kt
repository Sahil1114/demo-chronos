package io.github.sahilmakkar.chronos.scheduler.canceller

import io.github.sahilmakkar.chronos.db.WorkDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


actual class ChronosWorkCanceller(
    private val db: WorkDatabase,
    private val scope: CoroutineScope
) : WorkCanceller {
    actual override fun cancelWorkById(id: String) {
        scope.launch {
            db.workDao().updateWorkState(id, "CANCELLED")
            cancelDownstream(id)
        }
    }
    actual override fun cancelWorkByTag(tag: String) {
        scope.launch {
            db.workDao().getWorkRequestIdsByTag(tag)
                .forEach { cancelWorkById(it) }
        }
    }
    actual override fun cancelAllWork() {
        scope.launch {
            db.workDao().getAllWorkRequests()
                .forEach { db.workDao().updateWorkState(it.id, "CANCELLED") }
        }
    }
    private suspend fun cancelDownstream(parentId: String) {
        db.workDao().getDependenciesForParent(parentId).forEach { dep ->
            db.workDao().updateWorkState(dep.childWorkId, "CANCELLED")
            cancelDownstream(dep.childWorkId)
        }
    }
}