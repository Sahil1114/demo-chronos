package io.github.sahilmakkar.chronos

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Android WorkManager worker that wakes up Chronos to run pending tasks.
 *
 * @param context Android application context.
 * @param workerParams Execution parameters supplied by WorkManager.
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
