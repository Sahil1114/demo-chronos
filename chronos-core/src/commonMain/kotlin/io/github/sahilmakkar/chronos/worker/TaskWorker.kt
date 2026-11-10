package io.github.sahilmakkar.chronos.worker

import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.taskDataOf

/**
 * Base class for background units of work executed by Chronos.
 *
 * Applications implement this class to execute specific background operations, such as synchronizing
 * data with a remote server, processing local files, or triggering notifications.
 *
 * ### Lifecycle and Execution:
 * - A new worker instance is constructed by [WorkerRegistry] for each execution attempt.
 * - [doWork] is invoked on a background coroutine dispatcher.
 * - Long-running workers should periodically call [kotlinx.coroutines.ensureActive] or yield to support cooperative cancellation.
 * - Call [setProgress] to publish intermediate progression metrics to observers.
 *
 * ### Example Implementation:
 * ```kotlin
 * @RegisterWorker
 * class ImageUploadWorker(inputData: TaskData) : TaskWorker(inputData) {
 *     override suspend fun doWork(): TaskResult {
 *         val imageUri = inputData.getString("imageUri") ?: return TaskResult.Failure(
 *             outputData = taskDataOf("error" to "Missing imageUri")
 *         )
 *
 *         setProgress(taskDataOf("progress" to 50))
 *         val response = api.upload(imageUri)
 *
 *         return if (response.isSuccess) {
 *             TaskResult.Success(outputData = taskDataOf("remoteId" to response.id))
 *         } else if (response.isNetworkError) {
 *             TaskResult.Retry
 *         } else {
 *             TaskResult.Failure(outputData = taskDataOf("statusCode" to response.code))
 *         }
 *     }
 * }
 * ```
 *
 * @property inputData Immutable key-value input parameters delivered to this worker instance from [io.github.sahilmakkar.chronos.task.request.TaskRequest.taskData].
 *
 * @see TaskResult
 * @see WorkerRegistry
 * @see io.github.sahilmakkar.chronos.RegisterWorker
 */
public abstract class TaskWorker(public val inputData: TaskData = taskDataOf()) {

    /**
     * Executes the background task work.
     *
     * This method is invoked by the Chronos executor on a background thread pool.
     * Implementations must return:
     * - [TaskResult.Success] when the operation completes successfully.
     * - [TaskResult.Failure] when the operation fails unrecoverably.
     * - [TaskResult.Retry] when the operation encounters a transient failure and should be retried per backoff policy.
     *
     * If an uncaught [Exception] is thrown out of this method, Chronos catches it and automatically treats
     * the attempt as a [TaskResult.Failure] containing the exception message.
     *
     * @return Outcome of the execution pass.
     */
    public abstract suspend fun doWork(): TaskResult

    private var progressListener: (suspend (TaskData) -> Unit)? = null

    internal fun setProgressListener(listener: suspend (TaskData) -> Unit) {
        progressListener = listener
    }

    /**
     * Publishes intermediate progress data for this task execution to the database.
     *
     * Observers monitoring this task via [io.github.sahilmakkar.chronos.Chronos.observeTask] will
     * receive an updated [io.github.sahilmakkar.chronos.task.model.TaskInfo] containing [data].
     *
     * @param data Key-value progress payload observable by external callers.
     */
    public suspend fun setProgress(data: TaskData) {
        progressListener?.invoke(data)
    }
}
