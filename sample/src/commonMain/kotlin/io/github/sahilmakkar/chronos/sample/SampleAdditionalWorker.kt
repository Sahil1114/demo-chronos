package io.github.sahilmakkar.chronos.sample

import io.github.sahilmakkar.chronos.RegisterWorker
import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.worker.TaskWorker

@RegisterWorker
class SampleAdditionalWorker(inputData: TaskData) : TaskWorker(inputData) {
    override suspend fun doWork(): TaskResult = TaskResult.Success()
}
