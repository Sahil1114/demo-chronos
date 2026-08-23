package io.github.sahilmakkar.chronos.executor

import io.github.sahilmakkar.chronos.task.policy.BackoffPolicy
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.data.mapper.toJson
import io.github.sahilmakkar.chronos.task.model.workDataOf
import io.github.sahilmakkar.chronos.db.WorkDatabase
import io.github.sahilmakkar.chronos.db.WorkRequestEntity
import io.github.sahilmakkar.chronos.kmpWorker.KmpWorkerRegistry
import io.github.sahilmakkar.chronos.utils.KmpClock
import io.github.sahilmakkar.chronos.task.TaskResult
import kotlinx.coroutines.withTimeout

class WorkTaskExecutor(
    private val db: WorkDatabase,
    private val registry: KmpWorkerRegistry,
    private val clock: KmpClock
) {
    // Injected after construction to break the circular dependency
    // with WorkChainCoordinator
    lateinit var coordinator: WorkChainCoordinator

    suspend fun execute(entity: WorkRequestEntity, inputData: TaskData) {
        db.workDao().updateWorkState(entity.id, "RUNNING")
        db.workDao().updateRunAttemptCount(entity.id, entity.runAttemptCount + 1)

        val worker = try {
            registry.createWorker(entity.workerClassName, inputData)
        } catch (e: Exception) {
            db.workDao().updateWorkState(entity.id, "FAILED")
            return
        }

        worker.setProgressListener { db.workDao().updateWorkProgress(entity.id, it.toJson()) }

        val result = try {
            entity.timeoutMs?.takeIf { it > 0 }
                ?.let { withTimeout(it) { worker.doWork() } }
                ?: worker.doWork()
        } catch (e: Exception) {
            TaskResult.Failure(workDataOf("error" to (e.message ?: "Execution failed")))
        }

        handleResult(entity, result)
    }

    private suspend fun handleResult(entity: WorkRequestEntity, result: TaskResult) {
        when (result) {
            is TaskResult.Success -> {
                db.workDao().updateWorkStateAndOutput(entity.id, "SUCCEEDED", result.outputData.toJson())
                if (entity.repeatIntervalMs != null && entity.repeatIntervalMs > 0) {
                    db.workDao().updateScheduledAt(
                        entity.id,
                        clock.now().toEpochMilliseconds() + entity.repeatIntervalMs
                    )
                    db.workDao().updateWorkState(entity.id, "ENQUEUED")
                    db.workDao().updateRunAttemptCount(entity.id, 0)
                }
                coordinator.sweepChainRecovery()
                coordinator.runPendingTasks()
            }
            is TaskResult.Failure -> {
                db.workDao().updateWorkStateAndOutput(entity.id, "FAILED", result.outputData.toJson())
                coordinator.sweepChainRecovery()
                coordinator.runPendingTasks()
            }
            TaskResult.Retry -> {
                val attempt = entity.runAttemptCount + 1
                if (attempt >= entity.maxRetries) {
                    db.workDao().updateWorkState(entity.id, "FAILED")
                    coordinator.sweepChainRecovery()
                    coordinator.runPendingTasks()
                } else {
                    db.workDao().updateScheduledAt(
                        entity.id,
                        clock.now().toEpochMilliseconds() + computeBackoffDelay(entity, attempt)
                    )
                    db.workDao().updateWorkState(entity.id, "RETRYING")
                }
            }
        }
    }

    private fun computeBackoffDelay(entity: WorkRequestEntity, attempt: Int): Long {
        val policy = BackoffPolicy.valueOf(entity.backoffPolicy)
        return if (policy == BackoffPolicy.LINEAR) {
            entity.backoffDelayMs * attempt
        } else {
            entity.backoffDelayMs * (1L shl (attempt - 1))
        }
    }
}