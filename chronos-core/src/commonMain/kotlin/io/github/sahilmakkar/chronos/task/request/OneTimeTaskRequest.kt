package io.github.sahilmakkar.chronos.task.request

import io.github.sahilmakkar.chronos.task.constraints.EnvironmentConstraints
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.policy.BackoffPolicy
import io.github.sahilmakkar.chronos.worker.TaskWorker
import kotlin.reflect.KClass
import kotlin.time.Duration

/**
 * Non-recurring task request designed to execute once when its scheduled time, dependencies,
 * and environment constraints are satisfied.
 *
 * Once successfully completed, the task transitions to [io.github.sahilmakkar.chronos.task.TaskState.SUCCEEDED]
 * and will not run again. If execution fails, it retries up to [maxRetries] times according to its
 * [backoffPolicy] before reaching [io.github.sahilmakkar.chronos.task.TaskState.FAILED].
 *
 * Instances are created via [OneTimeTaskRequest.Builder].
 *
 * ### Example:
 * ```kotlin
 * val uploadRequest = OneTimeTaskRequest.Builder(UploadWorker::class)
 *     .setInputData(taskDataOf("filePath" to "/data/log.txt"))
 *     .setInitialDelay(5.minutes)
 *     .setConstraints(EnvironmentConstraints(requiredNetworkType = NetworkType.UNMETERED))
 *     .addTag("telemetry")
 *     .build()
 * ```
 *
 * @see PeriodicTaskRequest
 * @see TaskRequestBuilder
 */
public class OneTimeTaskRequest internal constructor(
    override val id: String,
    override val workerClassName: String,
    override val taskData: TaskData,
    override val constraints: EnvironmentConstraints,
    override val initialDelay: Duration,
    override val timeout: Duration?,
    override val backoffPolicy: BackoffPolicy,
    override val backoffDelay: Duration,
    override val maxRetries: Int,
    override val tags: Set<String>
) : TaskRequest() {
    override val isPeriodic: Boolean = false
    override val repeatInterval: Duration? = null

    /**
     * Builder for constructing a [OneTimeTaskRequest].
     *
     * @param workerClass Class reference of the [TaskWorker] that will perform the task.
     */
    public class Builder(workerClass: KClass<out TaskWorker>) :
        TaskRequestBuilder<OneTimeTaskRequest, Builder>(workerClass) {

        override fun self(): Builder = this

        override fun construct(): OneTimeTaskRequest = OneTimeTaskRequest(
            id = id,
            workerClassName = resolvedWorkerClassName,
            taskData = inputData,
            constraints = constraints,
            initialDelay = initialDelay,
            timeout = timeout,
            backoffPolicy = backoffPolicy,
            backoffDelay = backoffDelay,
            maxRetries = maxRetries,
            tags = tags.toSet()
        )
    }
}
