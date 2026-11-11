package io.github.sahilmakkar.chronos.task.request

import io.github.sahilmakkar.chronos.task.constraints.EnvironmentConstraints
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.policy.BackoffPolicy
import io.github.sahilmakkar.chronos.worker.TaskWorker
import kotlin.reflect.KClass
import kotlin.time.Duration

/**
 * Recurring task request that executes repeatedly at a fixed [repeatInterval].
 *
 * Periodic tasks remain scheduled indefinitely in persistent storage until explicitly cancelled via
 * [io.github.sahilmakkar.chronos.Chronos.cancelTaskById] or [io.github.sahilmakkar.chronos.Chronos.cancelTaskByTag].
 *
 * ### Failure and Rescheduling Semantics:
 * Following Android WorkManager semantics, periodic tasks survive execution failures:
 * - Upon a successful run, the attempt counter is reset and the task is rescheduled for `now + repeatInterval`.
 * - If a run fails or retries are exhausted, diagnostic output is persisted, the attempt counter resets to 0,
 *   and the task is rescheduled for the next interval period in the `ENQUEUED` state.
 *
 * Instances are configured and created via [PeriodicTaskRequest.Builder].
 *
 * ### Example:
 * ```kotlin
 * val syncRequest = PeriodicTaskRequest.Builder(SyncWorker::class, repeatInterval = 1.hours)
 *     .setConstraints(EnvironmentConstraints(requiresCharging = true))
 *     .addTag("periodic-sync")
 *     .build()
 * ```
 *
 * @see OneTimeTaskRequest
 * @see TaskRequestBuilder
 */
public class PeriodicTaskRequest internal constructor(
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
     * Builder for constructing a [PeriodicTaskRequest].
     *
     * @param workerClass Class reference of the [TaskWorker] that will perform the task.
     * @param repeatInterval Recurrence interval between successive task runs (must be greater than zero).
     */
    public class Builder(workerClass: KClass<out TaskWorker>, private val repeatInterval: Duration) :
        TaskRequestBuilder<PeriodicTaskRequest, Builder>(workerClass = workerClass) {

        override fun self(): Builder = this

        override fun validateSpecific() {
            require(value = repeatInterval > Duration.ZERO) {
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
