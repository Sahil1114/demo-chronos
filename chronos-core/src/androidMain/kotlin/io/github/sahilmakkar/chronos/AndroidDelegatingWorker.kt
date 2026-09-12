package io.github.sahilmakkar.chronos

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.fromJson
import io.github.sahilmakkar.chronos.task.model.toJson
import io.github.sahilmakkar.chronos.task.TaskResult

class AndroidDelegatingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val workerClassName = inputData.getString("worker_class_name") 
            ?: return Result.failure()
        val inputDataJson = inputData.getString("input_data_json") ?: "{}"
        
        val registry = Chronos.instance.registry
        val kmpWorker = try {
            registry.createWorker(workerClassName, TaskData.fromJson(json = inputDataJson))
        } catch (e: Exception) {
            return Result.failure()
        }

        kmpWorker.setProgressListener { progress ->
            setProgress(Data.Builder().putString("progress_data_json", progress.toJson()).build())
        }

        return when (val workResult = kmpWorker.doWork()) {
            is TaskResult.Success -> {
                val output = Data.Builder().putString("output_data_json", workResult.outputData.toJson()).build()
                Result.success(output)
            }
            is TaskResult.Failure -> {
                val output = Data.Builder().putString("output_data_json", workResult.outputData.toJson()).build()
                Result.failure(output)
            }
            TaskResult.Retry -> Result.retry()
        }
    }
}
