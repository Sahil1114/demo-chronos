package io.github.sahilmakkar.chronos.sample

import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.workDataOf
import io.github.sahilmakkar.chronos.worker.Worker
import io.github.sahilmakkar.chronos.worker.WorkerRegistry
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

class SampleSuccessWorker(inputData: TaskData) : Worker(inputData) {
    override suspend fun doWork(): TaskResult {
        setProgress(workDataOf("status" to "Working: step 1/2"))
        delay(1000.milliseconds)
        setProgress(workDataOf("status" to "Working: step 2/2"))
        delay(1000.milliseconds)
        return TaskResult.Success(workDataOf("message" to "Completed successfully!"))
    }
}

class SampleFailureWorker(inputData: TaskData) : Worker(inputData) {
    override suspend fun doWork(): TaskResult {
        setProgress(workDataOf("status" to "Processing..."))
        delay(1500.milliseconds)
        return TaskResult.Failure(workDataOf("error" to "Simulated task failure"))
    }
}

class SampleRetryWorker(inputData: TaskData) : Worker(inputData) {
    override suspend fun doWork(): TaskResult {
        setProgress(workDataOf("status" to "Attempting run..."))
        delay(1000.milliseconds)
        return TaskResult.Retry
    }
}

fun createSampleRegistry(): WorkerRegistry {
    return WorkerRegistry().apply {
        register(className = "io.github.sahilmakkar.chronos.sample.SampleSuccessWorker") {
            SampleSuccessWorker(
                inputData = it
            )
        }
        register(className = "io.github.sahilmakkar.chronos.sample.SampleFailureWorker") {
            SampleFailureWorker(
                inputData = it
            )
        }
        register(className = "io.github.sahilmakkar.chronos.sample.SampleRetryWorker") {
            SampleRetryWorker(
                inputData = it
            )
        }
    }
}