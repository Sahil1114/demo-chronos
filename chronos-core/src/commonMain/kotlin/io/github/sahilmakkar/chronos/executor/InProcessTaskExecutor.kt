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
import kotlinx.coroutines.withTimeout

internal class InProcessTaskExecutor(
    private val db: ChronosDatabase,
    private val registry: WorkerRegistry
) : TaskExecutor {

    override suspend fun execute(entity: TaskEntity, inputData: TaskData): TaskResult {
        db.taskDao().updateTaskState(id = entity.id, state = TaskState.RUNNING)
        db.taskDao().updateRunAttemptCount(id = entity.id, count = entity.runAttemptCount + 1)

        val worker = try {
            registry.createWorker(className = entity.workerClassName, inputData = inputData)
        } catch (e: Exception) {
            db.taskDao().updateTaskState(id = entity.id, state = TaskState.FAILED)
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
        } catch (e: Exception) {
            TaskResult.Failure(
                outputData = taskDataOf(
                    "error" to (e.message ?: "Execution failed")
                )
            )
        }
    }
}

@Deprecated("Use InProcessTaskExecutor instead", ReplaceWith("InProcessTaskExecutor"))
internal typealias WorkTaskExecutor = InProcessTaskExecutor
