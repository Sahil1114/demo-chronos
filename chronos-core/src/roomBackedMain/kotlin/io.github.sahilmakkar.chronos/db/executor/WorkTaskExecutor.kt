package io.github.sahilmakkar.chronos.db.executor

import io.github.sahilmakkar.chronos.data.mapper.toJson
import io.github.sahilmakkar.chronos.db.WorkDatabase
import io.github.sahilmakkar.chronos.db.WorkRequestEntity
import io.github.sahilmakkar.chronos.kmpWorker.KmpWorkerRegistry
import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.TaskState
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.workDataOf
import kotlinx.coroutines.withTimeout

/**
 * Responsible for executing a single task against its registered [KmpWorker].
 * Has no knowledge of chains, policies, or what happens after execution —
 * that is entirely the coordinator's concern.
 */
class WorkTaskExecutor(
    private val db: WorkDatabase,
    private val registry: KmpWorkerRegistry
) : TaskExecutor{

    /**
     * Executes the given task entity and returns the raw [TaskResult].
     * Sets task state to RUNNING before execution.
     * Handles progress reporting during execution.
     * Does NOT handle the result — the caller (coordinator) owns that.
     */
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