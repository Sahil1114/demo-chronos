package io.github.sahilmakkar.chronos.utils

import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.TaskInfo
import io.github.sahilmakkar.chronos.task.model.fromJson
import io.github.sahilmakkar.chronos.task.model.toJson
import io.github.sahilmakkar.chronos.roomDb.WorkRequestEntity
import io.github.sahilmakkar.chronos.task.request.TaskRequest
import io.github.sahilmakkar.chronos.task.TaskState


fun TaskRequest.toEntity(
    scheduledAt: Long,
    uniqueName: String? = null,
    chainId: String? = null,
    chainOrder: Int = 0
) = WorkRequestEntity(
    id = id,
    workerClassName = workerClassName,
    state = TaskState.ENQUEUED,
    uniqueWorkName = uniqueName,
    maxRetries = maxRetries,
    runAttemptCount = 0,
    timeoutMs = timeout?.inWholeMilliseconds,
    scheduledAt = scheduledAt,
    chainId = chainId,
    chainOrder = chainOrder,
    inputDataJson = taskData.toJson(),
    outputDataJson = "{}",
    progressDataJson = "{}",
    backoffPolicy = backoffPolicy,
    backoffDelayMs = backoffDelay.inWholeMilliseconds,
    networkType = constraints.requiredNetworkType,
    requiresCharging = constraints.requiresCharging,
    requiresBatteryNotLow = constraints.requiresBatteryNotLow,
    requiresStorageNotLow = constraints.requiresStorageNotLow,
    requiresDeviceIdle = constraints.requiresDeviceIdle,
    windowStartHour = constraints.allowedTimeWindow?.startHour,
    windowEndHour = constraints.allowedTimeWindow?.endHour,
    windowTimeZone = constraints.allowedTimeWindow?.timeZone,
    repeatIntervalMs = repeatInterval?.inWholeMilliseconds
)

fun WorkRequestEntity.toWorkInfo() = TaskInfo(
    id = id,
    state = state,
    outputData = TaskData.fromJson(outputDataJson),
    progress = TaskData.fromJson(progressDataJson),
    runAttemptCount = runAttemptCount
)