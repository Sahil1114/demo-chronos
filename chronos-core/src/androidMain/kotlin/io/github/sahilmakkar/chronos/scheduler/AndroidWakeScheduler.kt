package io.github.sahilmakkar.chronos.scheduler

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.sahilmakkar.chronos.ChronosWakeWorker
import io.github.sahilmakkar.chronos.storage.ChronosDatabase
import io.github.sahilmakkar.chronos.task.TaskState
import io.github.sahilmakkar.chronos.task.constraints.NetworkType
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

/**
 * Android [WakeScheduler] implementation scheduling [ChronosWakeWorker] requests via Android WorkManager.
 *
 * Implements Option B (the Wake Worker architecture):
 * 1. Queries the database for the earliest pending task in `ENQUEUED` or `RETRYING` states.
 * 2. If no tasks remain pending, cancels pending WorkManager wake requests.
 * 3. Maps hardware and network constraints from the earliest task to WorkManager [Constraints].
 * 4. Schedules a one-time [ChronosWakeWorker] delayed until `scheduledAt - now`, replacing any
 *    wake that has not started yet.
 *
 * Running wake workers are never cancelled by rescheduling. [scheduleNextWake] is called from inside
 * a running wake pass (by the coordinator and by `runPendingTasksNow`), and a `REPLACE` on the name
 * of the worker that is currently executing would cancel that worker together with the tasks it is
 * running. To avoid this, wakes alternate between two unique work names: the next wake is always
 * enqueued under a name that is not currently `RUNNING`, so at most one wake is running and one is
 * pending at any time.
 *
 * Limitation: the WorkManager constraints are taken from the earliest pending task only. A later task
 * with looser constraints is not woken earlier than the earliest task's constraints allow.
 */
internal class AndroidWakeScheduler(
    private val context: Context,
    private val db: ChronosDatabase,
    private val clock: Clock = Clock.System,
    private val workManager: WorkManager = WorkManager.getInstance(context = context),
) : WakeScheduler {
    /**
     * Inspects the database for the earliest pending task and schedules an Android WorkManager wake request.
     *
     * Queries [io.github.sahilmakkar.chronos.storage.TaskDao.getNextDueTask] for tasks in `ENQUEUED`
     * or `RETRYING` states. If no tasks remain, cancels every wake that is not currently running.
     * Otherwise, calculates the delay until `scheduledAt`, translates environment constraints
     * (network, battery charging, low battery, low storage, device idle) into Android WorkManager
     * [Constraints], and enqueues a one-time [ChronosWakeWorker] under the work name that is not running,
     * replacing any pending wake under that name and cancelling any pending wake under the other.
     */
    override suspend fun scheduleNextWake() {
        val states = listOf(TaskState.ENQUEUED, TaskState.RETRYING)
        val nextTask = db.taskDao().getNextDueTask(states = states)
        val running = runningWakeNames()

        if (nextTask == null) {
            cancelIdleWakes(running = running)
            return
        }

        val target = WAKE_WORK_NAMES.firstOrNull { it !in running } ?: return

        val nowMs = clock.now().toEpochMilliseconds()
        val delayMs = maxOf(a = 0L, b = nextTask.scheduledAt - nowMs)

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

        val request =
            OneTimeWorkRequestBuilder<ChronosWakeWorker>()
                .setInitialDelay(duration = delayMs, timeUnit = TimeUnit.MILLISECONDS)
                .setConstraints(constraints = constraintsBuilder.build())
                .build()

        workManager.enqueueUniqueWork(
            uniqueWorkName = target,
            existingWorkPolicy = ExistingWorkPolicy.REPLACE,
            request = request,
        )

        WAKE_WORK_NAMES
            .filter { it != target && it !in running }
            .forEach { workManager.cancelUniqueWork(uniqueWorkName = it) }
    }

    /**
     * Cancels every [ChronosWakeWorker] enqueued by Chronos, including a running one.
     *
     * Intended for shutdown. Rescheduling never goes through this method, because it would cancel the
     * wake pass that is calling it.
     */
    override fun cancelWake() {
        WAKE_WORK_NAMES.forEach { workManager.cancelUniqueWork(uniqueWorkName = it) }
    }

    private fun cancelIdleWakes(running: Set<String>) {
        WAKE_WORK_NAMES
            .filter { it !in running }
            .forEach { workManager.cancelUniqueWork(uniqueWorkName = it) }
    }

    // getWorkInfosForUniqueWorkFlow requires work-runtime 2.8.0 or newer; verify the version in libs.versions.toml.
    private suspend fun runningWakeNames(): Set<String> =
        WAKE_WORK_NAMES
            .filter { name ->
                workManager
                    .getWorkInfosForUniqueWorkFlow(uniqueWorkName = name)
                    .first()
                    .any { it.state == WorkInfo.State.RUNNING }
            }.toSet()

    companion object {
        /**
         * Primary unique work name for wake requests.
         */
        const val WAKE_WORK_NAME = "chronos_wake"

        /**
         * Alternate unique work name used while [WAKE_WORK_NAME] is running.
         */
        const val WAKE_WORK_NAME_ALT = "chronos_wake_alt"

        private val WAKE_WORK_NAMES = listOf(WAKE_WORK_NAME, WAKE_WORK_NAME_ALT)
    }
}
