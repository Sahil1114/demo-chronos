package io.github.sahilmakkar.chronos.storage.mapper

import io.github.sahilmakkar.chronos.storage.TaskEntity
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.TaskInfo
import io.github.sahilmakkar.chronos.task.model.fromJson

/**
 * Converts an internal persistent [TaskEntity] record into an immutable public [TaskInfo] snapshot.
 *
 * Deserializes JSON payloads for [TaskEntity.outputDataJson] and [TaskEntity.progressDataJson]
 * into immutable [TaskData] structures, extracting the current lifecycle [TaskState] and attempt count.
 *
 * @return An immutable [TaskInfo] snapshot representing the current persistent status of this task.
 */
internal fun TaskEntity.toTaskInfo(): TaskInfo = TaskInfo(
    id = id,
    state = state,
    outputData = TaskData.fromJson(outputDataJson),
    progress = TaskData.fromJson(progressDataJson),
    runAttemptCount = runAttemptCount
)
