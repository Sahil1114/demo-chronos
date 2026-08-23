package io.github.sahilmakkar.chronos.scheduler.canceller

import androidx.work.WorkManager
import java.util.UUID

actual class ChronosWorkCanceller(
    private val workManager: WorkManager
) : WorkCanceller {
    actual override fun cancelWorkById(id: String) {
        workManager.cancelWorkById(UUID.fromString(id))
    }
    actual override fun cancelWorkByTag(tag: String) {
        workManager.cancelAllWorkByTag(tag)
    }
    actual override fun cancelAllWork() {
        workManager.cancelAllWork()
    }
}