package io.github.sahilmakkar.chronos.task.request

import io.github.sahilmakkar.chronos.task.constraints.EnvironmentConstraints
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.policy.BackoffPolicy
import kotlin.time.Duration

/**
 * Base definition of a task request submitted to Chronos.
 *
 * @property id Canonical 36-character UUID string identifying this task.
 * @property workerClassName Fully-qualified class name of the [io.github.sahilmakkar.chronos.worker.TaskWorker] to execute.
 * @property taskData Key-value input parameters delivered to the worker at runtime.
 * @property constraints Environmental and device requirements that must be met before dispatch.
 * @property initialDelay Delay before the task becomes eligible for its initial run attempt.
 * @property timeout Optional maximum duration allowed for worker execution before timeout failure.
 * @property backoffPolicy Delay backoff algorithm used when scheduling retries.
 * @property backoffDelay Base duration unit used by [backoffPolicy] when computing retry delays.
 * @property maxRetries Maximum number of retries permitted after an initial failure (allowing `maxRetries + 1` total runs).
 * @property tags Arbitrary string tags attached to the task for group querying and bulk cancellation.
 * @property isPeriodic Indicates whether this task recurs periodically or runs only once.
 * @property repeatInterval Interval duration between recurring runs for periodic tasks, or null for one-time tasks.
 */
public sealed class TaskRequest {
    public abstract val id: String
    public abstract val workerClassName: String
    public abstract val taskData: TaskData
    public abstract val constraints: EnvironmentConstraints
    public abstract val initialDelay: Duration
    public abstract val timeout: Duration?
    public abstract val backoffPolicy: BackoffPolicy
    public abstract val backoffDelay: Duration
    public abstract val maxRetries: Int
    public abstract val tags: Set<String>
    public abstract val isPeriodic: Boolean
    public abstract val repeatInterval: Duration?
}
