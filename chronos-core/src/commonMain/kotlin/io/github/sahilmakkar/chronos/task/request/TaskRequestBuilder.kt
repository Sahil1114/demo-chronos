package io.github.sahilmakkar.chronos.task.request

import io.github.sahilmakkar.chronos.task.constraints.EnvironmentConstraints
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.taskDataOf
import io.github.sahilmakkar.chronos.task.policy.BackoffPolicy
import io.github.sahilmakkar.chronos.worker.TaskWorker
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.uuid.Uuid

sealed class TaskRequestBuilder<T : TaskRequest, B : TaskRequestBuilder<T, B>>(
    private val workerClass: KClass<out TaskWorker>
) {
    protected var id: String = Uuid.random().toHexString()
    protected var inputData: TaskData = taskDataOf()
    protected var constraints: EnvironmentConstraints = EnvironmentConstraints()
    protected var initialDelay: Duration = Duration.ZERO
    protected var timeout: Duration? = null
    protected var backoffPolicy: BackoffPolicy = BackoffPolicy.EXPONENTIAL
    protected var backoffDelay: Duration = Duration.parse("30s")
    protected var maxRetries: Int = 3
    protected val tags = mutableSetOf<String>()

    protected abstract fun self(): B

    fun setInputData(inputData: TaskData): B = self().also { this.inputData = inputData }

    fun setConstraints(constraints: EnvironmentConstraints): B =
        self().also { this.constraints = constraints }

    fun setInitialDelay(delay: Duration): B = self().also { this.initialDelay = delay }

    fun setTimeout(timeout: Duration): B = self().also { this.timeout = timeout }

    fun setBackoffCriteria(policy: BackoffPolicy, initialDelay: Duration, maxRetries: Int): B =
        self().also {
            this.backoffPolicy = policy
            this.backoffDelay = initialDelay
            this.maxRetries = maxRetries
        }

    fun addTag(tag: String): B = self().also { this.tags.add(tag) }

    protected val resolvedWorkerClassName: String
        get() = workerClass.qualifiedName
            ?: throw IllegalArgumentException("Worker class must have a qualified name")

    private fun validateCommon() {
        require(id.isNotBlank()) {
            "id must not be blank"
        }
        require(maxRetries >= 0) {
            "maxRetries must be >= 0, was $maxRetries"
        }
        require(!backoffDelay.isNegative()) {
            "backoffDelay must not be negative"
        }
        require(!initialDelay.isNegative()) {
            "initialDelay must not be negative"
        }
        timeout?.let { require(!it.isNegative()) {
            "timeout must not be negative" }
        }
    }

    protected open fun validateSpecific() {}

    protected abstract fun construct(): T

    fun build(): T {
        validateCommon()
        validateSpecific()
        return construct()
    }
}
