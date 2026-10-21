package io.github.sahilmakkar.chronos.engine

import io.github.sahilmakkar.chronos.port.ConstraintVerifier
import io.github.sahilmakkar.chronos.port.TaskExecutor
import io.github.sahilmakkar.chronos.storage.ChronosDatabase
import io.github.sahilmakkar.chronos.storage.TaskEntity
import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.TaskState
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.fromJson
import io.github.sahilmakkar.chronos.task.model.taskDataOf
import io.github.sahilmakkar.chronos.task.model.toJson
import io.github.sahilmakkar.chronos.task.policy.BackoffPolicy
import io.github.sahilmakkar.chronos.task.policy.DependentTaskPolicy
import kotlin.time.Clock

internal class TaskCoordinator(
    private val db: ChronosDatabase,
    private val taskExecutor: TaskExecutor,
    private val constraintProvider: ConstraintVerifier
) {

    suspend fun sweepOrphans() {
        db.taskDao().getTasksByState(TaskState.RUNNING).forEach {
            db.taskDao().updateTaskState(id = it.id, state = TaskState.ENQUEUED)
        }
    }

    suspend fun sweepChainRecovery() {
        db.taskDao().getAllDependencies().forEach { dep ->
            val parent = db.taskDao().getTask(id = dep.parentTaskId) ?: return@forEach
            val child = db.taskDao().getTask(id = dep.childTaskId) ?: return@forEach
            when {
                parent.state == TaskState.SUCCEEDED && child.state == TaskState.ENQUEUED ->
                    db.taskDao().updateScheduledAt(
                        id = child.id,
                        scheduledAt = Clock.System.now().toEpochMilliseconds()
                    )

                parent.state == TaskState.FAILED && child.state == TaskState.ENQUEUED ->
                    if (dep.chainFailurePolicy == DependentTaskPolicy.STOP_ON_FAILURE) {
                        db.taskDao().updateTaskState(id = child.id, state = TaskState.FAILED)
                    } else {
                        db.taskDao().updateScheduledAt(
                            id = child.id,
                            scheduledAt = Clock.System.now().toEpochMilliseconds()
                        )
                    }
            }
        }
    }

    suspend fun runPendingTasks() {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val pending = db.taskDao().getTasksByState(TaskState.ENQUEUED) +
                db.taskDao().getTasksByState(TaskState.RETRYING)

        for (entity in pending) {
            if (entity.scheduledAt > nowMs) continue
            if (!resolveDependencies(entity = entity)) continue
            if (!constraintProvider.isSatisfied(entity)) continue

            val inputData = TaskData.fromJson(entity.inputDataJson)
            val result = taskExecutor.execute(entity, inputData)
            handleResult(entity, result)
        }
    }

    private suspend fun handleResult(entity: TaskEntity, result: TaskResult) {
        when (result) {
            is TaskResult.Success -> {
                // Handle periodic task rescheduling
                if (entity.repeatIntervalMs != null && entity.repeatIntervalMs > 0) {
                    db.taskDao().updateRunAttemptCount(id = entity.id, count = 0)
                    db.taskDao().updateScheduledAt(
                        id = entity.id,
                        scheduledAt = Clock.System.now().toEpochMilliseconds() + entity.repeatIntervalMs
                    )
                    db.taskDao().updateTaskStateAndOutput(
                        id = entity.id,
                        state = TaskState.ENQUEUED,
                        outputJson = result.outputData.toJson()
                    )
                } else {
                    db.taskDao().updateTaskStateAndOutput(
                        id = entity.id,
                        state = TaskState.SUCCEEDED,
                        outputJson = result.outputData.toJson()
                    )
                }
                // Trigger chain recovery and next pending tasks
                sweepChainRecovery()
                runPendingTasks()
            }

            is TaskResult.Failure -> {
                db.taskDao().updateTaskStateAndOutput(
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
                    db.taskDao().updateTaskStateAndOutput(
                        id = entity.id,
                        state = TaskState.FAILED,
                        outputJson = taskDataOf("error" to "Max retries (${entity.maxRetries}) exhausted").toJson()
                    )
                    sweepChainRecovery()
                    runPendingTasks()
                } else {
                    db.taskDao().updateScheduledAt(
                        id = entity.id,
                        scheduledAt = Clock.System.now()
                            .toEpochMilliseconds() + computeBackoffDelay(entity, attempt)
                    )
                    db.taskDao().updateTaskState(id = entity.id, state = TaskState.RETRYING)
                }
            }
        }
    }

    private suspend fun resolveDependencies(entity: TaskEntity): Boolean {
        val deps = db.taskDao().getDependenciesForChild(entity.id)
        for (dep in deps) {
            val parent = db.taskDao().getTask(dep.parentTaskId) ?: return false
            when (parent.state) {
                TaskState.SUCCEEDED -> continue
                TaskState.FAILED -> {
                    if (dep.chainFailurePolicy == DependentTaskPolicy.STOP_ON_FAILURE) {
                        db.taskDao().updateTaskState(id = entity.id, state = TaskState.FAILED)
                        return false
                    } else {
                        continue
                    }
                }
                TaskState.CANCELLED -> {
                    db.taskDao().updateTaskState(id = entity.id, state = TaskState.CANCELLED)
                    return false
                }
                else -> return false
            }
        }
        return true
    }

    private fun computeBackoffDelay(entity: TaskEntity, attempt: Int): Long {
        return when (entity.backoffPolicy) {
            BackoffPolicy.LINEAR -> entity.backoffDelayMs * attempt
            BackoffPolicy.EXPONENTIAL -> entity.backoffDelayMs * (1L shl (attempt - 1))
        }
    }
}

@Deprecated("Use TaskCoordinator instead", ReplaceWith("TaskCoordinator"))
internal typealias WorkChainCoordinator = TaskCoordinator
