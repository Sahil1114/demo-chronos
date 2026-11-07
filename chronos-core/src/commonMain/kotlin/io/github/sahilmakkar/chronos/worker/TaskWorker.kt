package io.github.sahilmakkar.chronos.worker

import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.taskDataOf

/**
 * Base class for background units of work executed by Chronos.
 *
 * @property inputData Immutable key-value input parameters supplied to this worker.
 */
public abstract class TaskWorker(public val inputData: TaskData = taskDataOf()) {

    /**
     * Executes the background task logic.
     *
     * @return [TaskResult] representing success, retry, or terminal failure.
     */
    public abstract suspend fun doWork(): TaskResult

    private var progressListener: (suspend (TaskData) -> Unit)? = null

    internal fun setProgressListener(listener: suspend (TaskData) -> Unit) {
        progressListener = listener
    }

    /**
     * Updates real-time progress data for this task execution.
     */
    public suspend fun setProgress(data: TaskData) {
        progressListener?.invoke(data)
    }
}
