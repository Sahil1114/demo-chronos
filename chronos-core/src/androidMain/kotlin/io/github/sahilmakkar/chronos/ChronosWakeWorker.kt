package io.github.sahilmakkar.chronos

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Android WorkManager worker that wakes up Chronos to run pending tasks.
 */
public class ChronosWakeWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

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
