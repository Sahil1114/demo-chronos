package io.github.sahilmakkar.chronos.task.model

import io.github.sahilmakkar.chronos.task.TaskState

data class TaskInfo(
    val id: String,
    val state: TaskState,
    val outputData: TaskData,
    val progress: TaskData,
    val runAttemptCount: Int
)