package io.github.sahilmakkar.chronos.task.request

import io.github.sahilmakkar.chronos.kmpWorker.KmpWorker
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.workDataOf
import io.github.sahilmakkar.chronos.task.policy.BackoffPolicy
import io.github.sahilmakkar.chronos.task.constraints.EnvironmentConstraints
import io.github.sahilmakkar.chronos.utils.randomUUIDString
import kotlin.reflect.KClass
import kotlin.time.Duration

/**
 * Shared builder state and validation for [TaskRequest] subtypes.
 *
 * @param T the concrete [TaskRequest] type this builder produces.
 * @param B the concrete builder type, used to return `this` typed correctly from setters.
 */
sealed class TaskRequestBuilder<T : TaskRequest, B : TaskRequestBuilder<T, B>>(
    private val workerClass: KClass<out KmpWorker>
) {
    protected var id: String = randomUUIDString()
    protected var inputData: TaskData = workDataOf()
    protected var constraints: EnvironmentConstraints = EnvironmentConstraints()
    protected var initialDelay: Duration = Duration.ZERO
    protected var timeout: Duration? = null
    protected var backoffPolicy: BackoffPolicy = BackoffPolicy.EXPONENTIAL
    protected var backoffDelay: Duration = Duration.parse("30s")
    protected var maxRetries: Int = 3
    protected val tags = mutableSetOf<String>()

    /** Returns `this` typed as [B]. Each concrete builder implements this as `return this`. */
    protected abstract fun self(): B

    /** Sets input parameters for the worker. */
    fun setInputData(inputData: TaskData): B = self().also { this.inputData = inputData }

    /** Sets requirements/constraints for execution. */
    fun setConstraints(constraints: EnvironmentConstraints): B =
        self().also { this.constraints = constraints }

    /** Sets initial delay before the work starts. */
    fun setInitialDelay(delay: Duration): B = self().also { this.initialDelay = delay }

    /** Sets a timeout duration limit for the execution of the worker. */
    fun setTimeout(timeout: Duration): B = self().also { this.timeout = timeout }

    /**
     * Sets the backoff retry criteria.
     *
     * @param policy The backoff policy.
     * @param initialDelay Initial wait duration before retrying.
     * @param maxRetries Maximum retry attempt count before failing the task permanently.
     */
    fun setBackoffCriteria(policy: BackoffPolicy, initialDelay: Duration, maxRetries: Int): B =
        self().also {
            this.backoffPolicy = policy
            this.backoffDelay = initialDelay
            this.maxRetries = maxRetries
        }

    /** Adds a queryable string tag to group or locate the task. */
    fun addTag(tag: String): B = self().also { this.tags.add(tag) }

    /** Resolves the worker's qualified class name, failing fast if unavailable. */
    protected val resolvedWorkerClassName: String
        get() = workerClass.qualifiedName
            ?: throw IllegalArgumentException("Worker class must have a qualified name")

    /** Shared field validation. Subtypes should call this from [build] before constructing. */
    private fun validateCommon() {
        require(id.isNotBlank()) { "Task id must not be blank" }
        require(maxRetries >= 0) { "maxRetries must be >= 0, was $maxRetries" }
        require(!backoffDelay.isNegative()) { "backoffDelay must not be negative" }
        require(!initialDelay.isNegative()) { "initialDelay must not be negative" }
        timeout?.let { require(!it.isNegative()) { "timeout must not be negative" } }
    }

    /** Subtype-specific checks beyond the common fields (e.g. repeatInterval > 0). No-op by default. */
    protected open fun validateSpecific() {}

    /** Constructs [T] from already-validated state. Called only after both validations pass. */
    protected abstract fun construct(): T

    fun build(): T {
        validateCommon()
        validateSpecific()
        return construct()
    }
}