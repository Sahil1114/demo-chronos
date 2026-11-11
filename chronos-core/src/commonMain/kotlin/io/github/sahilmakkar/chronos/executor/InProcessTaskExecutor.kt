package io.github.sahilmakkar.chronos.executor

import io.github.sahilmakkar.chronos.port.TaskExecutor
import io.github.sahilmakkar.chronos.storage.ChronosDatabase
import io.github.sahilmakkar.chronos.storage.TaskEntity
import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.TaskState
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.taskDataOf
import io.github.sahilmakkar.chronos.task.model.toJson
import io.github.sahilmakkar.chronos.worker.WorkerRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * In-process implementation of [TaskExecutor] that instantiates and runs [io.github.sahilmakkar.chronos.worker.TaskWorker] instances.
 *
 * Responsibilities:
 * 1. Resolves worker factories via [WorkerRegistry].
 * 2. Hooks the worker's progress callbacks to persist live progress updates into Room SQLite.
 * 3. Enforces execution timeouts via [withTimeout] if configured on the task entity.
 * 4. Catches exceptions and returns appropriate [TaskResult.Failure] objects.
 */
internal class InProcessTaskExecutor(
    private val db: ChronosDatabase,
    private val registry: WorkerRegistry
) : TaskExecutor {

    /**
     * Instantiates and runs the worker for [entity], delivering [inputData] and returning the execution outcome.
     *
     * Execution flow:
     * 1. Resolves worker constructor from [WorkerRegistry]. If unmapped, returns [TaskResult.Failure].
     * 2. Hooks the worker's progress listener to persist intermediate updates into SQLite via [io.github.sahilmakkar.chronos.storage.TaskDao.updateTaskProgress].
     * 3. Executes [io.github.sahilmakkar.chronos.worker.TaskWorker.doWork] wrapped with [withTimeout] if [TaskEntity.timeoutMs] is set.
     * 4. Converts timeout exceptions into [TaskResult.Failure] with timeout diagnostic details.
     * 5. Re-throws [CancellationException] to honor coroutine cooperative cancellation.
     * 6. Converts all other exceptions into [TaskResult.Failure] with the exception message.
     *
     * @param entity Persistent task entity record.
     * @param inputData Input parameters passed to the worker.
     * @return Result of worker execution.
     */
    override suspend fun execute(entity: TaskEntity, inputData: TaskData): TaskResult {
        val worker = try {
            registry.createWorker(className = entity.workerClassName, inputData = inputData)
        } catch (e: Exception) {
            return TaskResult.Failure(outputData = taskDataOf("error" to "Worker not registered: ${entity.workerClassName}"))
        }

        worker.setProgressListener { progress ->
            db.taskDao().updateTaskProgress(
                id = entity.id,
                progressJson = progress.toJson()
            )
        }

        return try {
            val timeout = entity.timeoutMs
            if (timeout != null && timeout > 0) {
                withTimeout(timeMillis = timeout) { worker.doWork() }
            } else {
                worker.doWork()
            }
        } catch (e: TimeoutCancellationException) {
            TaskResult.Failure(
                outputData = taskDataOf(
                    "error" to "Task timed out after ${entity.timeoutMs}ms"
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TaskResult.Failure(
                outputData = taskDataOf(
                    "error" to (e.message ?: "Execution failed")
                )
            )
        }
    }
}
