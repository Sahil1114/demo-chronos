package io.github.sahilmakkar.chronos.task.request

import io.github.sahilmakkar.chronos.worker.Worker
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.policy.BackoffPolicy
import io.github.sahilmakkar.chronos.task.constraints.EnvironmentConstraints
import kotlin.reflect.KClass
import kotlin.time.Duration

class OneTimeTaskRequest internal constructor(
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

    class Builder(workerClass: KClass<out Worker>) :
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