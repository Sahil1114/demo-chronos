package io.github.sahilmakkar.chronos.task.model

import io.github.sahilmakkar.chronos.task.TaskState

/**
 * Snapshot of a task's current status and diagnostic details.
 *
 * @property id Unique identifier of the task.
 * @property state Current lifecycle state of the task (e.g. ENQUEUED, RUNNING, SUCCEEDED).
 * @property outputData Output payload produced by the task upon completion or failure.
 * @property progress Intermediate progress data emitted by the running worker.
 * @property runAttemptCount Number of times this task has attempted execution.
 */
public data class TaskInfo(
    public val id: String,
    public val state: TaskState,
    public val outputData: TaskData,
    public val progress: TaskData,
    public val runAttemptCount: Int
)
