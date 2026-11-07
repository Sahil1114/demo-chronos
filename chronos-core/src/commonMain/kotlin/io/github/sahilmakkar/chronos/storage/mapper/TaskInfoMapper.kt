package io.github.sahilmakkar.chronos.storage.mapper

import io.github.sahilmakkar.chronos.storage.TaskEntity
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.TaskInfo
import io.github.sahilmakkar.chronos.task.model.fromJson

/**
 * Converts a persistent [TaskEntity] to a public [TaskInfo] snapshot.
 */
internal fun TaskEntity.toTaskInfo(): TaskInfo = TaskInfo(
    id = id,
    state = state,
    outputData = TaskData.fromJson(outputDataJson),
    progress = TaskData.fromJson(progressDataJson),
    runAttemptCount = runAttemptCount
)
