package io.github.sahilmakkar.chronos.storage.mapper

import io.github.sahilmakkar.chronos.storage.TaskEntity
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.TaskInfo
import io.github.sahilmakkar.chronos.task.model.fromJson

fun TaskEntity.toTaskInfo() = TaskInfo(
    id = id,
    state = state,
    outputData = TaskData.fromJson(outputDataJson),
    progress = TaskData.fromJson(progressDataJson),
    runAttemptCount = runAttemptCount
)

@Deprecated("Use toTaskInfo() instead", ReplaceWith("toTaskInfo()"))
fun TaskEntity.toWorkInfo() = toTaskInfo()
