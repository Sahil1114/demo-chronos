package io.github.sahilmakkar.chronos.data.mapper

import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.TaskInfo
import io.github.sahilmakkar.chronos.db.WorkRequestEntity
import io.github.sahilmakkar.chronos.task.TaskState

fun WorkRequestEntity.toWorkInfo() = TaskInfo(
    id = id,
    state = TaskState.valueOf(state),
    outputData = TaskData.fromJson(outputDataJson),
    progress = TaskData.fromJson(progressDataJson),
    runAttemptCount = runAttemptCount
)