package io.github.sahilmakkar.chronos.storage.mapper

import io.github.sahilmakkar.chronos.storage.TaskEntity
import io.github.sahilmakkar.chronos.task.TaskState
import io.github.sahilmakkar.chronos.task.model.toJson
import io.github.sahilmakkar.chronos.task.request.TaskRequest

/**
 * Converts a high-level public [TaskRequest] specification into a persistable [TaskEntity] database record.
 *
 * Maps all user-configured parameters into database columns:
 * - Initializes state to [TaskState.ENQUEUED].
 * - Serializes input [TaskData] into JSON.
 * - Extracts millisecond values for timeouts, delays, and backoff durations.
 * - Flattens nested [io.github.sahilmakkar.chronos.task.constraints.EnvironmentConstraints] (network, battery, storage, time window).
 * - Binds optional unique names, chain grouping identifiers, and sequential chain ordering.
 *
 * @param scheduledAt Epoch millisecond timestamp when this task becomes eligible for dispatch.
 * @param uniqueName Optional unique task name if enqueued via unique task APIs.
 * @return A fully populated [TaskEntity] ready for insertion into Room SQLite.
 */
internal fun TaskRequest.toEntity(
    scheduledAt: Long,
    uniqueName: String? = null,
): TaskEntity = TaskEntity(
    id = id,
    workerClassName = workerClassName,
    state = TaskState.ENQUEUED,
    uniqueTaskName = uniqueName,
    maxRetries = maxRetries,
    runAttemptCount = 0,
    timeoutMs = timeout?.inWholeMilliseconds,
    scheduledAt = scheduledAt,
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
