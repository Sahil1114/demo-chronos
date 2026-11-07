package io.github.sahilmakkar.chronos.engine

import io.github.sahilmakkar.chronos.port.ConstraintVerifier
import io.github.sahilmakkar.chronos.port.TaskExecutor
import io.github.sahilmakkar.chronos.scheduler.WakeScheduler
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * Central coordinator responsible for task claiming, dependency resolution, execution dispatch, and result handling.
 */
internal class TaskCoordinator(
    private val db: ChronosDatabase,
    private val taskExecutor: TaskExecutor,
    private val constraintProvider: ConstraintVerifier,
    private val clock: Clock = Clock.System,
    private val maxConcurrentTasks: Int = 4,
    private val wakeScheduler: WakeScheduler? = null
) {

    private val semaphore = Semaphore(maxConcurrentTasks)

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
                        scheduledAt = clock.now().toEpochMilliseconds()
                    )

                parent.state == TaskState.FAILED && child.state == TaskState.ENQUEUED ->
                    if (dep.chainFailurePolicy == DependentTaskPolicy.STOP_ON_FAILURE) {
                        db.taskDao().updateTaskState(id = child.id, state = TaskState.FAILED)
                    } else {
                        db.taskDao().updateScheduledAt(
                            id = child.id,
                            scheduledAt = clock.now().toEpochMilliseconds()
                        )
                    }

                parent.state == TaskState.CANCELLED && (child.state == TaskState.ENQUEUED || child.state == TaskState.RETRYING) ->
                    db.taskDao().updateTaskState(id = child.id, state = TaskState.CANCELLED)
            }
        }
    }

    suspend fun runPendingTasks(): Int {
        var totalExecuted = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val nowMs = clock.now().toEpochMilliseconds()
            val pending = db.taskDao().getTasksByState(TaskState.ENQUEUED) +
                    db.taskDao().getTasksByState(TaskState.RETRYING)

            val eligible = pending.filter { entity ->
                entity.scheduledAt <= nowMs &&
                        resolveDependencies(entity = entity) &&
                        constraintProvider.isSatisfied(entity)
            }.sortedBy { it.scheduledAt }

            if (eligible.isEmpty()) {
                break
            }

            var claimedInPass = 0
            val passMutex = Mutex()
            coroutineScope {
                for (entity in eligible) {
                    launch {
                        semaphore.withPermit {
                            val claimed = db.taskDao().claim(
                                id = entity.id,
                                running = TaskState.RUNNING,
                                claimable = listOf(TaskState.ENQUEUED, TaskState.RETRYING)
                            )
                            if (claimed == 0) return@withPermit

                            passMutex.withLock { claimedInPass++ }
                            val attemptCount = entity.runAttemptCount + 1
                            db.taskDao().updateRunAttemptCount(id = entity.id, count = attemptCount)
                            val runningEntity = entity.copy(state = TaskState.RUNNING, runAttemptCount = attemptCount)

                            try {
                                val inputData = TaskData.fromJson(runningEntity.inputDataJson)
                                val result = taskExecutor.execute(runningEntity, inputData)
                                handleResult(runningEntity, result)
                            } catch (e: CancellationException) {
                                withContext(NonCancellable) {
                                    val current = db.taskDao().getTask(runningEntity.id)
                                    if (current != null && current.state != TaskState.CANCELLED) {
                                        db.taskDao().updateTaskState(id = runningEntity.id, state = TaskState.ENQUEUED)
                                    }
                                }
                                throw e
                            } catch (e: Exception) {
                                val failureResult = TaskResult.Failure(
                                    outputData = taskDataOf("error" to (e.message ?: "Task execution failed"))
                                )
                                handleResult(runningEntity, failureResult)
                            }
                        }
                    }
                }
            }

            totalExecuted += claimedInPass
            if (claimedInPass == 0) {
                break
            }
        }
        return totalExecuted
    }

    private suspend fun handleResult(entity: TaskEntity, result: TaskResult) {
        val current = db.taskDao().getTask(entity.id)
        if (current?.state == TaskState.CANCELLED) {
            sweepChainRecovery()
            return
        }

        when (result) {
            is TaskResult.Success -> {
                if (entity.repeatIntervalMs != null && entity.repeatIntervalMs > 0) {
                    db.taskDao().updateRunAttemptCount(id = entity.id, count = 0)
                    db.taskDao().updateScheduledAt(
                        id = entity.id,
                        scheduledAt = clock.now().toEpochMilliseconds() + entity.repeatIntervalMs
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
                sweepChainRecovery()
            }

            is TaskResult.Failure -> {
                if (entity.repeatIntervalMs != null && entity.repeatIntervalMs > 0) {
                    db.taskDao().updateRunAttemptCount(id = entity.id, count = 0)
                    db.taskDao().updateScheduledAt(
                        id = entity.id,
                        scheduledAt = clock.now().toEpochMilliseconds() + entity.repeatIntervalMs
                    )
                    db.taskDao().updateTaskStateAndOutput(
                        id = entity.id,
                        state = TaskState.ENQUEUED,
                        outputJson = result.outputData.toJson()
                    )
                } else {
                    db.taskDao().updateTaskStateAndOutput(
                        id = entity.id,
                        state = TaskState.FAILED,
                        outputJson = result.outputData.toJson()
                    )
                }
                sweepChainRecovery()
            }

            TaskResult.Retry -> {
                val attempt = entity.runAttemptCount
                if (attempt > entity.maxRetries) {
                    if (entity.repeatIntervalMs != null && entity.repeatIntervalMs > 0) {
                        db.taskDao().updateRunAttemptCount(id = entity.id, count = 0)
                        db.taskDao().updateScheduledAt(
                            id = entity.id,
                            scheduledAt = clock.now().toEpochMilliseconds() + entity.repeatIntervalMs
                        )
                        db.taskDao().updateTaskStateAndOutput(
                            id = entity.id,
                            state = TaskState.ENQUEUED,
                            outputJson = taskDataOf("error" to "Max retries (${entity.maxRetries}) exhausted for interval").toJson()
                        )
                    } else {
                        db.taskDao().updateTaskStateAndOutput(
                            id = entity.id,
                            state = TaskState.FAILED,
                            outputJson = taskDataOf("error" to "Max retries (${entity.maxRetries}) exhausted").toJson()
                        )
                    }
                    sweepChainRecovery()
                } else {
                    db.taskDao().updateScheduledAt(
                        id = entity.id,
                        scheduledAt = clock.now()
                            .toEpochMilliseconds() + computeBackoffDelay(entity, attempt)
                    )
                    db.taskDao().updateTaskState(id = entity.id, state = TaskState.RETRYING)
                }
            }
        }
        wakeScheduler?.scheduleNextWake()
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

    internal fun computeBackoffDelay(entity: TaskEntity, attempt: Int): Long {
        return entity.backoffPolicy.calculateDelay(entity.backoffDelayMs, attempt)
    }
}
