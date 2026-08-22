package io.github.sahilmakkar.chronos.task.request

import io.github.sahilmakkar.chronos.kmpWorker.KmpWorker
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.policy.BackoffPolicy
import io.github.sahilmakkar.chronos.task.constraints.EnvironmentConstraints
import kotlin.reflect.KClass
import kotlin.time.Duration

/**
 * A [TaskRequest] that runs periodically at regular intervals.
 */
class PeriodicTaskRequest(
    override val id: String,
    override val workerClassName: String,
    override val taskData: TaskData,
    override val constraints: EnvironmentConstraints,
    override val initialDelay: Duration,
    override val timeout: Duration?,
    override val backoffPolicy: BackoffPolicy,
    override val backoffDelay: Duration,
    override val maxRetries: Int,
    override val tags: Set<String>,
    override val repeatInterval: Duration
) : TaskRequest() {
    override val isPeriodic: Boolean = true

    /**
     * Builder to construct a [PeriodicTaskRequest].
     *
     * @param workerClass The KClass of the worker to execute.
     * @param repeatInterval The interval at which the task should recur.
     */
    class Builder(workerClass: KClass<out KmpWorker>, private val repeatInterval: Duration) :
        TaskRequestBuilder<PeriodicTaskRequest, Builder>(workerClass) {

        override fun self(): Builder = this

        override fun validateSpecific() {
            require(repeatInterval > Duration.ZERO) {
                "repeatInterval must be greater than zero, was $repeatInterval"
            }
        }

        override fun construct(): PeriodicTaskRequest = PeriodicTaskRequest(
            id = id,
            workerClassName = resolvedWorkerClassName,
            taskData = inputData,
            constraints = constraints,
            initialDelay = initialDelay,
            timeout = timeout,
            backoffPolicy = backoffPolicy,
            backoffDelay = backoffDelay,
            maxRetries = maxRetries,
            tags = tags,
            repeatInterval = repeatInterval
        )
    }
}