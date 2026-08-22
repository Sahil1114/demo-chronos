package io.github.sahilmakkar.chronos.task.request

import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.policy.BackoffPolicy
import io.github.sahilmakkar.chronos.task.constraints.EnvironmentConstraints
import kotlin.time.Duration


/**
 * Base abstract class defining a background task request.
 */
sealed class TaskRequest {
    abstract val id: String
    abstract val workerClassName: String
    abstract val taskData: TaskData
    abstract val constraints: EnvironmentConstraints
    abstract val initialDelay: Duration
    abstract val timeout: Duration?
    abstract val backoffPolicy: BackoffPolicy
    abstract val backoffDelay: Duration
    abstract val maxRetries: Int
    abstract val tags: Set<String>
    abstract val isPeriodic: Boolean
    abstract val repeatInterval: Duration?
}
