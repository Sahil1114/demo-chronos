package io.github.sahilmakkar.chronos.task

import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.workDataOf


sealed class TaskResult {

    data class Success(val outputData: TaskData = workDataOf()) : TaskResult()

    data class Failure(val outputData: TaskData = workDataOf()) : TaskResult()

    object Retry : TaskResult()
}