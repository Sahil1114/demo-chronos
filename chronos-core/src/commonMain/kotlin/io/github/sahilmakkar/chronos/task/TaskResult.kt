package io.github.sahilmakkar.chronos.task

import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.taskDataOf

sealed class TaskResult {

    data class Success(val outputData: TaskData = taskDataOf()) : TaskResult()

    data class Failure(val outputData: TaskData = taskDataOf()) : TaskResult()

    object Retry : TaskResult()
}
