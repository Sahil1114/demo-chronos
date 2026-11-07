package io.github.sahilmakkar.chronos.task.model

import io.github.sahilmakkar.chronos.task.TaskState

/**
 * Snapshot of a task's current status and diagnostic details.
 */
public data class TaskInfo(
    public val id: String,
    public val state: TaskState,
    public val outputData: TaskData,
    public val progress: TaskData,
    public val runAttemptCount: Int
)
