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
 * Android [WakeScheduler] implementation scheduling [ChronosWakeWorker] requests via Android WorkManager.
 *
 * Implements Option B (the Wake Worker architecture):
 * 1. Queries the database for the earliest pending task in `ENQUEUED` or `RETRYING` states.
 * 2. If no tasks remain pending, cancels existing WorkManager wake requests.
 * 3. Maps hardware and network constraints from the earliest task to WorkManager [Constraints].
 * 4. Schedules a unique one-time [ChronosWakeWorker] using [ExistingWorkPolicy.REPLACE] delayed until `scheduledAt - now`.
 */
internal class ChronosWakeScheduler(
    private val context: Context,
    private val db: ChronosDatabase,
    private val clock: Clock = Clock.System,
    private val workManager: WorkManager = WorkManager.getInstance(context)
) : WakeScheduler {

    /**
     * Inspects the database for the earliest pending task and schedules an Android WorkManager wake request.
     *
     * Queries [io.github.sahilmakkar.chronos.storage.TaskDao.getNextDueTask] for tasks in `ENQUEUED`
     * or `RETRYING` states. If no tasks remain, cancels any existing wake work via [cancelWake].
     * Otherwise, calculates the delay until `scheduledAt`, translates environment constraints
     * (network, battery charging, low battery, low storage, device idle) into Android WorkManager
     * [Constraints], and enqueues a unique one-time [ChronosWakeWorker] replacing any existing scheduled wake.
     */
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

    /**
     * Cancels any pending unique [ChronosWakeWorker] enqueued in Android WorkManager.
     */
    override fun cancelWake() {
        workManager.cancelUniqueWork(WAKE_WORK_NAME)
    }

    companion object {
        /**
         * Unique work name used to ensure only one wake request is queued at any time.
         */
        const val WAKE_WORK_NAME = "chronos_wake"
    }
}
