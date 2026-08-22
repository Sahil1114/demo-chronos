package io.github.sahilmakkar.chronos.task.model

import io.github.sahilmakkar.chronos.task.TaskState

/**
 * Encapsulates the execution state and metadata of a task.
 *
 * @property id The unique identifier of the task.
 * @property state The current lifecycle state (e.g. ENQUEUED, RUNNING).
 * @property outputData Output payload produced if the task succeeded/failed.
 * @property progress Intermediate progress data published by the task.
 * @property runAttemptCount Number of execution attempts completed (starts at 0).
 */
data class TaskInfo(
    val id: String,
    val state: TaskState,
    val outputData: TaskData,
    val progress: TaskData,
    val runAttemptCount: Int
)