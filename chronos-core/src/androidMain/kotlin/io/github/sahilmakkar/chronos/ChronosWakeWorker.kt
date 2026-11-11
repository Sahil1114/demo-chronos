package io.github.sahilmakkar.chronos

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Android [CoroutineWorker] that wakes up the application process to dispatch due Chronos tasks.
 *
 * Chronos utilizes WorkManager strictly as a background wake mechanism rather than an execution engine.
 * When a task is enqueued with a future scheduled execution time, [io.github.sahilmakkar.chronos.scheduler.ChronosWakeScheduler]
 * registers a one-off `ChronosWakeWorker` scheduled for the earliest pending task.
 *
 * When triggered by the Android OS, this worker obtains the process-wide [Chronos.Companion.instance] and invokes
 * [Chronos.runPendingTasksNow] to execute all eligible tasks.
 *
 * @param context Android application context.
 * @param workerParams Execution parameters supplied by Android WorkManager.
 *
 * @see io.github.sahilmakkar.chronos.scheduler.ChronosWakeScheduler
 */
public class ChronosWakeWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    /**
     * Executes the wake pass by delegating to [Chronos.runPendingTasksNow].
     */
    public override suspend fun doWork(): Result {
        val chronos = try {
            Chronos.instance
        } catch (e: Throwable) {
            return Result.retry()
        }

        return try {
            chronos.runPendingTasksNow()
            Result.success()
        } catch (e: Throwable) {
            Result.retry()
        }
    }
}
