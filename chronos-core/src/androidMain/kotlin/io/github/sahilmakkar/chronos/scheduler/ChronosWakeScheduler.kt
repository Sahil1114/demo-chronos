package io.github.sahilmakkar.chronos.scheduler

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.github.sahilmakkar.chronos.ChronosWakeWorker
import io.github.sahilmakkar.chronos.storage.ChronosDatabase
import io.github.sahilmakkar.chronos.task.TaskState
import io.github.sahilmakkar.chronos.task.constraints.NetworkType
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

/**
 * Schedules Android WorkManager wake-up requests matching next due task execution times.
 */
internal class ChronosWakeScheduler(
    private val context: Context,
    private val db: ChronosDatabase,
    private val clock: Clock = Clock.System,
    private val workManager: WorkManager = WorkManager.getInstance(context)
) : WakeScheduler {

    override suspend fun scheduleNextWake() {
        val states = listOf(TaskState.ENQUEUED, TaskState.RETRYING)
        val nextTask = db.taskDao().getNextDueTask(states)
        if (nextTask == null) {
            cancelWake()
            return
        }

        val nowMs = clock.now().toEpochMilliseconds()
        val delayMs = maxOf(0L, nextTask.scheduledAt - nowMs)

        val constraintsBuilder = Constraints.Builder()
        when (nextTask.networkType) {
            NetworkType.CONNECTED -> constraintsBuilder.setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            NetworkType.UNMETERED -> constraintsBuilder.setRequiredNetworkType(androidx.work.NetworkType.UNMETERED)
            NetworkType.NOT_REQUIRED -> constraintsBuilder.setRequiredNetworkType(androidx.work.NetworkType.NOT_REQUIRED)
        }
        if (nextTask.requiresCharging) {
            constraintsBuilder.setRequiresCharging(true)
        }
        if (nextTask.requiresBatteryNotLow) {
            constraintsBuilder.setRequiresBatteryNotLow(true)
        }
        if (nextTask.requiresStorageNotLow) {
            constraintsBuilder.setRequiresStorageNotLow(true)
        }
        if (nextTask.requiresDeviceIdle) {
            constraintsBuilder.setRequiresDeviceIdle(true)
        }

        val request = OneTimeWorkRequestBuilder<ChronosWakeWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(constraintsBuilder.build())
            .build()

        workManager.enqueueUniqueWork(
            WAKE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    override fun cancelWake() {
        workManager.cancelUniqueWork(WAKE_WORK_NAME)
    }

    companion object {
        const val WAKE_WORK_NAME = "chronos_wake"
    }
}
