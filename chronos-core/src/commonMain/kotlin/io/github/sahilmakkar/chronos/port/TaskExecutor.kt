package io.github.sahilmakkar.chronos.port

import io.github.sahilmakkar.chronos.storage.TaskEntity
import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.model.TaskData

/**
 * Port contract for executing a task worker given its entity and input payload.
 */
internal interface TaskExecutor {
    suspend fun execute(entity: TaskEntity, inputData: TaskData): TaskResult
}
