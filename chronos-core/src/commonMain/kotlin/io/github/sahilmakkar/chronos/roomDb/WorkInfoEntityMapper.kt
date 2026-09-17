package io.github.sahilmakkar.chronos.roomDb

import io.github.sahilmakkar.chronos.task.model.fromJson
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.TaskInfo

fun WorkRequestEntity.toWorkInfo() = TaskInfo(
    id = id,
    state = state,
    outputData = TaskData.fromJson(outputDataJson),
    progress = TaskData.fromJson(progressDataJson),
    runAttemptCount = runAttemptCount
)