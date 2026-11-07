package io.github.sahilmakkar.chronos.task.request

import io.github.sahilmakkar.chronos.task.constraints.EnvironmentConstraints
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.policy.BackoffPolicy
import kotlin.time.Duration

/**
 * Base definition of a task request submitted to Chronos.
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
