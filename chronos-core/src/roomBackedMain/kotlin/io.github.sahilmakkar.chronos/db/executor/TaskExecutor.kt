package io.github.sahilmakkar.chronos.db.executor


import io.github.sahilmakkar.chronos.db.WorkRequestEntity
import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.model.TaskData

/**
 * Executes a single task and returns its raw result.
 * Has no knowledge of chains, policies, or dependency resolution —
 * that is entirely WorkChainCoordinator's concern.
 */
interface TaskExecutor {
    suspend fun execute(entity: WorkRequestEntity, inputData: TaskData): TaskResult
}