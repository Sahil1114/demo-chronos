package io.github.sahilmakkar.chronos.port

import io.github.sahilmakkar.chronos.storage.TaskEntity
import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.model.TaskData

/**
 * Internal architecture port responsible for running worker logic and returning execution outcomes.
 *
 * Decouples task execution semantics (instantiation, reflection-free lookup, progress callback hooking,
 * timeouts, and exception catching) from database and concurrency coordination.
 */
internal interface TaskExecutor {

    /**
     * Executes the task worker corresponding to [entity] using the provided [inputData].
     *
     * Implementations:
     * 1. Resolve and instantiate the worker via [io.github.sahilmakkar.chronos.worker.WorkerRegistry].
     * 2. Hook progress emission callbacks to write intermediate progress to the database.
     * 3. Apply execution timeout constraints if [TaskEntity.timeoutMs] is set.
     * 4. Catch unexpected runtime exceptions, converting them into [TaskResult.Failure] values
     *    while re-throwing [kotlinx.coroutines.CancellationException] to preserve coroutine cancellation integrity.
     *
     * @param entity The persistent task entity representing the job to be executed.
     * @param inputData The input data payload to be delivered to the worker.
     * @return The resulting [TaskResult] indicating [TaskResult.Success], [TaskResult.Failure],
     *   or [TaskResult.Retry].
     */
    suspend fun execute(entity: TaskEntity, inputData: TaskData): TaskResult
}
