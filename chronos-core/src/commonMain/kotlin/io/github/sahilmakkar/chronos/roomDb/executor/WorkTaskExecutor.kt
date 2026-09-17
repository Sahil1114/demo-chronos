package io.github.sahilmakkar.chronos.db.executor

import io.github.sahilmakkar.chronos.task.model.toJson
import io.github.sahilmakkar.chronos.roomDb.WorkDatabase
import io.github.sahilmakkar.chronos.roomDb.WorkRequestEntity
import io.github.sahilmakkar.chronos.worker.WorkerRegistry
import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.TaskState
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.workDataOf
import kotlinx.coroutines.withTimeout

class WorkTaskExecutor(
    private val db: WorkDatabase,
    private val registry: WorkerRegistry
) : TaskExecutor{

    override suspend fun execute(entity: WorkRequestEntity, inputData: TaskData): TaskResult {
        db.workDao().updateWorkState(id = entity.id, state = TaskState.RUNNING)
        db.workDao().updateRunAttemptCount(id = entity.id, count = entity.runAttemptCount + 1)

        val worker = try {
            registry.createWorker(className = entity.workerClassName, inputData = inputData)
        } catch (e: Exception) {
            db.workDao().updateWorkState(id = entity.id, state = TaskState.FAILED)
            return TaskResult.Failure(outputData = workDataOf("error" to "Worker not registered: ${entity.workerClassName}"))
        }

        worker.setProgressListener { progress ->
            db.workDao().updateWorkProgress(
                id = entity.id,
                progressJson = progress.toJson()
            )
        }

        return try {
            entity.timeoutMs?.takeIf { it > 0 }
                ?.let { timeout -> withTimeout(timeout) { worker.doWork() } }
                ?: worker.doWork()
        } catch (e: Exception) {
            TaskResult.Failure(
                outputData = workDataOf(
                    "error" to (e.message ?: "Execution failed")
                )
            )
        }
    }
}