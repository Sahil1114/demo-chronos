package io.github.sahilmakkar.chronos.scheduler.canceller

import androidx.work.WorkManager
import io.github.sahilmakkar.chronos.roomDb.WorkDatabase
import io.github.sahilmakkar.chronos.task.TaskState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

actual class ChronosWorkCanceller(
    private val workManager: WorkManager,
    private val db: WorkDatabase,
    private val coroutineScope: CoroutineScope
) : WorkCanceller {

    actual override fun cancelWorkById(id: String) {
        // Cancel in WorkManager if currently executing
        workManager.cancelWorkById(id = UUID.fromString(id))
        // Update Room state -- source of truth
        coroutineScope.launch {
            db.workDao().updateWorkState(id = id, state = TaskState.CANCELLED)
        }
    }

    actual override fun cancelWorkByTag(tag: String) {
        workManager.cancelAllWorkByTag(tag)
        coroutineScope.launch {
            db.workDao().getWorkRequestIdsByTag(tag)
                .forEach { cancelWorkById(it) }
        }
    }

    actual override fun cancelAllWork() {
        workManager.cancelAllWork()
        coroutineScope.launch {
            db.workDao().getAllWorkRequests().forEach {
                db.workDao().updateWorkState(id = it.id, state = TaskState.CANCELLED)
            }
        }
    }
}