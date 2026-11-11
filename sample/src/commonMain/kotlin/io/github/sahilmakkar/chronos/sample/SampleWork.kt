package io.github.sahilmakkar.chronos.sample

import io.github.sahilmakkar.chronos.RegisterWorker
import io.github.sahilmakkar.chronos.registerGeneratedWorkers
import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.taskDataOf
import io.github.sahilmakkar.chronos.worker.TaskWorker
import io.github.sahilmakkar.chronos.worker.WorkerRegistry
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@RegisterWorker
class SampleSuccessWorker(inputData: TaskData) : TaskWorker(inputData = inputData) {
    override suspend fun doWork(): TaskResult {
        setProgress(data = taskDataOf("status" to "Working: step 1/2"))
        delay(duration = 1000.milliseconds)
        setProgress(data = taskDataOf("status" to "Working: step 2/2"))
        delay(duration = 1000.milliseconds)
        return TaskResult.Success(outputData = taskDataOf("message" to "Completed successfully!"))
    }
}

@RegisterWorker
class SampleFailureWorker(inputData: TaskData) : TaskWorker(inputData = inputData) {
    override suspend fun doWork(): TaskResult {
        setProgress(data = taskDataOf("status" to "Processing..."))
        delay(duration = 1500.milliseconds)
        return TaskResult.Failure(outputData = taskDataOf("error" to "Simulated task failure"))
    }
}

@RegisterWorker
class SampleRetryWorker(inputData: TaskData) : TaskWorker(inputData = inputData) {
    override suspend fun doWork(): TaskResult {
        setProgress(data = taskDataOf("status" to "Attempting run..."))
        delay(duration = 1000.milliseconds)
        return TaskResult.Retry
    }
}

fun createSampleRegistry(): WorkerRegistry {
    return WorkerRegistry().apply {
        registerGeneratedWorkers()
    }
}
