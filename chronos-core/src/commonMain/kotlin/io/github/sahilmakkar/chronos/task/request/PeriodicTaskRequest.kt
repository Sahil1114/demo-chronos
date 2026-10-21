package io.github.sahilmakkar.chronos.task.request

import io.github.sahilmakkar.chronos.task.constraints.EnvironmentConstraints
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.policy.BackoffPolicy
import io.github.sahilmakkar.chronos.worker.TaskWorker
import kotlin.reflect.KClass
import kotlin.time.Duration

class PeriodicTaskRequest internal constructor(
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

    class Builder(workerClass: KClass<out TaskWorker>, private val repeatInterval: Duration) :
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
            tags = tags.toSet(),
            repeatInterval = repeatInterval
        )
    }
}
