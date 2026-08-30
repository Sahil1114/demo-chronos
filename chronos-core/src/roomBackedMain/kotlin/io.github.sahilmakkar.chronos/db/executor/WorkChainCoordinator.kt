package io.github.sahilmakkar.chronos.db.executor

import io.github.sahilmakkar.chronos.data.mapper.fromJson
import io.github.sahilmakkar.chronos.data.mapper.toJson
import io.github.sahilmakkar.chronos.db.WorkDatabase
import io.github.sahilmakkar.chronos.db.WorkRequestEntity
import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.TaskState
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.workDataOf
import io.github.sahilmakkar.chronos.task.policy.BackoffPolicy
import io.github.sahilmakkar.chronos.task.policy.DependentTaskPolicy
import io.github.sahilmakkar.chronos.utils.KmpClock

/**
 * Coordinates chain-aware task scheduling:
 * - Sweeps orphaned RUNNING tasks back to ENQUEUED on startup
 * - Resolves chain dependencies before a task is eligible to run
 * - Recovers chain state after a parent completes
 * - Drives the pending task scan loop
 * - Owns all result handling logic after execution
 */
class WorkChainCoordinator(
    private val db: WorkDatabase,
    private val clock: KmpClock,
    private val taskExecutor: TaskExecutor
) {

    suspend fun sweepOrphans() {
        db.workDao().getWorkRequestsByState(TaskState.RUNNING).forEach {
            db.workDao().updateWorkState(id = it.id, state = TaskState.ENQUEUED)
        }
    }

    suspend fun sweepChainRecovery() {
        db.workDao().getAllDependencies().forEach { dep ->
            val parent = db.workDao().getWorkRequest(id = dep.parentWorkId) ?: return@forEach
            val child = db.workDao().getWorkRequest(id = dep.childWorkId) ?: return@forEach
            when {
                parent.state == TaskState.SUCCEEDED && child.state == TaskState.ENQUEUED ->
                    db.workDao().updateScheduledAt(
                        id = child.id,
                        scheduledAt = clock.now().toEpochMilliseconds()
                    )

                parent.state == TaskState.FAILED && child.state == TaskState.ENQUEUED ->
                    if (dep.chainFailurePolicy == DependentTaskPolicy.STOP_ON_FAILURE) {
                        db.workDao().updateWorkState(id = child.id, state = TaskState.FAILED)
                    } else {
                        db.workDao().updateScheduledAt(
                            id = child.id,
                            scheduledAt = clock.now().toEpochMilliseconds()
                        )
                    }
            }
        }
    }

    suspend fun runPendingTasks() {
        val nowMs = clock.now().toEpochMilliseconds()
        val pending = db.workDao().getWorkRequestsByState(TaskState.ENQUEUED) +
                db.workDao().getWorkRequestsByState(TaskState.RETRYING)

        for (entity in pending) {
            if (entity.scheduledAt > nowMs) continue
            if (!resolveDependencies(entity = entity)) continue
            // if (!constraintVerifier.isSatisfied(entity)) continue

            val inputData = TaskData.fromJson(entity.inputDataJson)
            val result = taskExecutor.execute(entity, inputData)
            handleResult(entity, result)
        }
    }

    private suspend fun handleResult(entity: WorkRequestEntity, result: TaskResult) {
        when (result) {
            is TaskResult.Success -> {
                db.workDao().updateWorkStateAndOutput(
                    id = entity.id,
                    state = TaskState.SUCCEEDED,
                    outputJson = result.outputData.toJson()
                )
                // Handle periodic task rescheduling
                if (entity.repeatIntervalMs != null && entity.repeatIntervalMs > 0) {
                    db.workDao().updateScheduledAt(
                        id = entity.id,
                        scheduledAt = clock.now().toEpochMilliseconds() + entity.repeatIntervalMs
                    )
                    db.workDao().updateWorkState(id = entity.id, state = TaskState.ENQUEUED)
                    db.workDao().updateRunAttemptCount(id = entity.id, count = 0)
                }
                // Trigger chain recovery and next pending tasks
                sweepChainRecovery()
                runPendingTasks()
            }

            is TaskResult.Failure -> {
                db.workDao().updateWorkStateAndOutput(
                    id = entity.id,
                    state = TaskState.FAILED,
                    outputJson = result.outputData.toJson()
                )
                sweepChainRecovery()
                runPendingTasks()
            }

            TaskResult.Retry -> {
                val attempt = entity.runAttemptCount + 1
                if (attempt >= entity.maxRetries) {
                    // Exhausted retries — treat as permanent failure
                    db.workDao().updateWorkStateAndOutput(
                        id = entity.id,
                        state = TaskState.FAILED,
                        outputJson = workDataOf("error" to "Max retries (${entity.maxRetries}) exhausted").toJson()
                    )
                    sweepChainRecovery()
                    runPendingTasks()
                } else {
                    db.workDao().updateScheduledAt(
                        id = entity.id,
                        scheduledAt = clock.now()
                            .toEpochMilliseconds() + computeBackoffDelay(entity, attempt)
                    )
                    db.workDao().updateWorkState(id = entity.id, state = TaskState.RETRYING)
                    // No chain recovery needed for retry — task isn't done yet
                }
            }
        }
    }

    private suspend fun resolveDependencies(entity: WorkRequestEntity): Boolean {
        val deps = db.workDao().getDependenciesForChild(entity.id)
        for (dep in deps) {
            val parent = db.workDao().getWorkRequest(dep.parentWorkId)
            if (parent == null || parent.state != TaskState.SUCCEEDED) {
                if (parent?.state == TaskState.FAILED && dep.chainFailurePolicy == DependentTaskPolicy.STOP_ON_FAILURE) {
                    db.workDao().updateWorkState(id = entity.id, state = TaskState.FAILED)
                }
                return false
            }
        }
        return true
    }

    private fun computeBackoffDelay(entity: WorkRequestEntity, attempt: Int): Long {
        return when (entity.backoffPolicy) {
            BackoffPolicy.LINEAR -> entity.backoffDelayMs * attempt
            BackoffPolicy.EXPONENTIAL -> entity.backoffDelayMs * (1L shl (attempt - 1))
        }
    }
}