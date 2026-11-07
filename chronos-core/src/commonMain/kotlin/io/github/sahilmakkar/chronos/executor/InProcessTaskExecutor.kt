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

internal class InProcessTaskExecutor(
    private val db: ChronosDatabase,
    private val registry: WorkerRegistry
) : TaskExecutor {

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
                withTimeout(timeout) { worker.doWork() }
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
