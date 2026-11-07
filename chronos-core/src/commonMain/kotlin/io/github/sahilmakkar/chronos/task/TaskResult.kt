package io.github.sahilmakkar.chronos.task

import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.taskDataOf

/**
 * Result returned by a [io.github.sahilmakkar.chronos.worker.TaskWorker] upon completing execution.
 */
public sealed class TaskResult {

    /**
     * Successful completion, optionally carrying output data.
     */
    public data class Success(public val outputData: TaskData = taskDataOf()) : TaskResult()

    /**
     * Permanent failure, optionally carrying error diagnostic data.
     */
    public data class Failure(public val outputData: TaskData = taskDataOf()) : TaskResult()

    /**
     * Transient failure indicating that the task should be retried per backoff policy.
     */
    public data object Retry : TaskResult()
}
