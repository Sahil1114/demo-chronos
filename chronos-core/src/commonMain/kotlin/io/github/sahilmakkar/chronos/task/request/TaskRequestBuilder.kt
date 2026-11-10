package io.github.sahilmakkar.chronos.task.request

import io.github.sahilmakkar.chronos.task.constraints.EnvironmentConstraints
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.taskDataOf
import io.github.sahilmakkar.chronos.task.policy.BackoffPolicy
import io.github.sahilmakkar.chronos.worker.TaskWorker
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.uuid.Uuid

/**
 * Base builder for configuring common parameters of a [TaskRequest].
 *
 * Provides a fluent API to configure execution constraints, input payloads, retry backoff algorithms,
 * initial scheduling delays, timeouts, and diagnostic tags.
 *
 * ### Default Values:
 * - **id:** Random canonical 36-character dashed UUID (`kotlin.uuid.Uuid.random().toString()`).
 * - **inputData:** Empty [TaskData] payload.
 * - **constraints:** Default [EnvironmentConstraints] (no network, battery, or idle restrictions).
 * - **initialDelay:** `Duration.ZERO` (immediate execution eligibility upon enqueue).
 * - **timeout:** `null` (no execution timeout enforced).
 * - **backoffPolicy:** [BackoffPolicy.EXPONENTIAL].
 * - **backoffDelay:** 30 seconds.
 * - **maxRetries:** 3 retries (permitting 4 total execution attempts).
 * - **tags:** Empty set.
 *
 * @param T The concrete [TaskRequest] type produced by this builder.
 * @param B The concrete builder subtype, returned by fluent setter methods.
 * @property workerClass Kotlin reflection reference to the [TaskWorker] implementation class.
 */
public sealed class TaskRequestBuilder<T : TaskRequest, B : TaskRequestBuilder<T, B>>(
    private val workerClass: KClass<out TaskWorker>
) {
    protected var id: String = Uuid.random().toString()
    protected var inputData: TaskData = taskDataOf()
    protected var constraints: EnvironmentConstraints = EnvironmentConstraints()
    protected var initialDelay: Duration = Duration.ZERO
    protected var timeout: Duration? = null
    protected var backoffPolicy: BackoffPolicy = BackoffPolicy.EXPONENTIAL
    protected var backoffDelay: Duration = Duration.parse("30s")
    protected var maxRetries: Int = 3
    protected val tags: MutableSet<String> = mutableSetOf()

    protected abstract fun self(): B

    /**
     * Sets the key-value input data payload supplied to the worker at runtime.
     */
    public fun setInputData(inputData: TaskData): B = self().also { this.inputData = inputData }

    /**
     * Sets environmental requirements (network, battery, charging, time window) for executing this task.
     */
    public fun setConstraints(constraints: EnvironmentConstraints): B =
        self().also { this.constraints = constraints }

    /**
     * Sets the duration to wait before the task first becomes eligible for execution.
     */
    public fun setInitialDelay(delay: Duration): B = self().also { this.initialDelay = delay }

    /**
     * Sets the maximum execution duration permitted for the worker before timing out.
     */
    public fun setTimeout(timeout: Duration): B = self().also { this.timeout = timeout }

    /**
     * Sets the retry policy, base backoff duration, and maximum retry count for failed runs.
     *
     * @param policy Delay progression algorithm (LINEAR or EXPONENTIAL).
     * @param initialDelay Base duration multiplier used by the backoff algorithm.
     * @param maxRetries Maximum number of retries permitted after initial failure (allowing `maxRetries + 1` total attempts).
     */
    public fun setBackoffCriteria(policy: BackoffPolicy, initialDelay: Duration, maxRetries: Int): B =
        self().also {
            this.backoffPolicy = policy
            this.backoffDelay = initialDelay
            this.maxRetries = maxRetries
        }

    /**
     * Adds an arbitrary categorization tag to this task request.
     */
    public fun addTag(tag: String): B = self().also { this.tags.add(tag) }

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

    /**
     * Validates configuration parameters and constructs the concrete [TaskRequest] instance.
     */
    public fun build(): T {
        validateCommon()
        validateSpecific()
        return construct()
    }
}
