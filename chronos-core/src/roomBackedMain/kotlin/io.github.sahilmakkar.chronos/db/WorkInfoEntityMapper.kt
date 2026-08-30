package io.github.sahilmakkar.chronos.db

import io.github.sahilmakkar.chronos.data.mapper.fromJson
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.TaskInfo

fun WorkRequestEntity.toWorkInfo() = TaskInfo(
    id = id,
    state = state,
    outputData = TaskData.fromJson(outputDataJson),
    progress = TaskData.fromJson(progressDataJson),
    runAttemptCount = runAttemptCount
)