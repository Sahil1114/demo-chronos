package io.github.sahilmakkar.chronos.scheduler

import io.github.sahilmakkar.chronos.engine.TaskCoordinator
import io.github.sahilmakkar.chronos.port.TaskEnqueuer
import io.github.sahilmakkar.chronos.storage.ChronosDatabase
import io.github.sahilmakkar.chronos.storage.TaskDependencyEntity
import io.github.sahilmakkar.chronos.storage.TaskEntity
import io.github.sahilmakkar.chronos.storage.TaskTagEntity
import io.github.sahilmakkar.chronos.storage.mapper.toEntity
import io.github.sahilmakkar.chronos.task.TaskChain
import io.github.sahilmakkar.chronos.task.policy.DependentTaskPolicy
import io.github.sahilmakkar.chronos.task.policy.UniqueTaskPolicy
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import io.github.sahilmakkar.chronos.task.request.PeriodicTaskRequest
import io.github.sahilmakkar.chronos.task.request.TaskRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Default implementation of [TaskEnqueuer] managing database task insertions and execution triggers.
 *
 * Persists task requests, schedules platform wake-ups via [WakeScheduler], and notifies [TaskCoordinator]
 * to evaluate pending work immediately.
 */
internal class ChronosTaskEnqueuer(
    private val db: ChronosDatabase,
    private val coroutineScope: CoroutineScope,
    private val coordinator: TaskCoordinator,
    private val clock: Clock = Clock.System,
    private val onEnqueued: ((TaskRequest) -> Unit)? = null,
    private val wakeScheduler: WakeScheduler? = null
) : TaskEnqueuer {

    /**
     * Enqueues a unique one-time task applying the specified collision [policy].
     *
     * If an existing task shares [name]:
     * - [UniqueTaskPolicy.KEEP]: Leaves the active task intact; drops [request].
     * - [UniqueTaskPolicy.REPLACE]: Cancels the existing active task and downstream dependents, inserting [request].
     * - [UniqueTaskPolicy.APPEND]: Attaches [request] as a sequential dependency behind the current leaf of the existing chain.
     *
     * @param name Unique task name identifier.
     * @param policy Strategy for handling collisions with existing tasks sharing the same name.
     * @param request One-time task specification to enqueue.
     */
    override fun enqueueTask(
        name: String,
        policy: UniqueTaskPolicy,
        request: OneTimeTaskRequest
    ) {
        coroutineScope.launch {
            val existing = db.taskDao().getTaskByName(name)
            if (existing != null) {
                when (policy) {
                    UniqueTaskPolicy.KEEP -> return@launch
                    UniqueTaskPolicy.REPLACE -> cancelAndReplace(existingId = existing.id)
                    UniqueTaskPolicy.APPEND -> {
                        val leaf = findLeafTask(existing.id)
                        insertWithDependency(
                            request = request,
                            uniqueName = name,
                            parentId = leaf.id
                        )
                        onEnqueued?.invoke(request)
                        coordinator.sweepChainRecovery()
                        wakeScheduler?.scheduleNextWake()
                        coordinator.runPendingTasks()
                        return@launch
                    }
                }
            }
            insertUnique(request, name)
            onEnqueued?.invoke(request)
            wakeScheduler?.scheduleNextWake()
            coordinator.runPendingTasks()
        }
    }

    /**
     * Enqueues a unique periodic task applying [UniqueTaskPolicy.KEEP] or [UniqueTaskPolicy.REPLACE].
     *
     * @param name Unique task name identifier.
     * @param policy Collision resolution strategy. Note: [UniqueTaskPolicy.APPEND] is rejected.
     * @param request Periodic task specification to enqueue.
     * @throws IllegalArgumentException if [policy] is [UniqueTaskPolicy.APPEND].
     */
    override fun enqueuePeriodicTask(
        name: String,
        policy: UniqueTaskPolicy,
        request: PeriodicTaskRequest
    ) {
        require(value = policy != UniqueTaskPolicy.APPEND) {
            "APPEND policy is not supported for unique periodic work."
        }
        coroutineScope.launch {
            val existing = db.taskDao().getTaskByName(name)
            if (existing != null) {
                when (policy) {
                    UniqueTaskPolicy.KEEP -> return@launch
                    UniqueTaskPolicy.REPLACE -> cancelAndReplace(existingId = existing.id)
                    UniqueTaskPolicy.APPEND -> return@launch
                }
            }
            insertUnique(request, name)
            onEnqueued?.invoke(request)
            wakeScheduler?.scheduleNextWake()
            coordinator.runPendingTasks()
        }
    }

    /**
     * Enqueues a sequential [TaskChain] within a single atomic database transaction.
     *
     * Tasks, tags, and parent-child dependency edges are persisted simultaneously.
     *
     * @param chain The chained sequence of task requests.
     */
    override fun enqueue(chain: TaskChain) {
        if (chain.requests.isEmpty()) return
        coroutineScope.launch {
            val nowMs = clock.now().toEpochMilliseconds()
            val tasks = mutableListOf<TaskEntity>()
            val tags = mutableListOf<TaskTagEntity>()
            val dependencies = mutableListOf<TaskDependencyEntity>()

            chain.requests.forEachIndexed { i, req ->
                tasks.add(
                    req.toEntity(
                        scheduledAt = nowMs + req.initialDelay.inWholeMilliseconds,
                    )
                )
                tags.addAll(req.tags.map { TaskTagEntity(req.id, it) })
                if (i > 0) {
                    dependencies.add(
                        TaskDependencyEntity(
                            parentTaskId = chain.requests[i - 1].id,
                            childTaskId = req.id,
                            chainFailurePolicy = chain.failurePolicies[i - 1]
                        )
                    )
                }
            }
            db.taskDao().insertChain(tasks, tags, dependencies)
            chain.requests.forEach { onEnqueued?.invoke(it) }
            wakeScheduler?.scheduleNextWake()
            coordinator.runPendingTasks()
        }
    }

    private suspend fun insertUnique(request: TaskRequest, name: String) {
        db.taskDao().insertTask(
            request.toEntity(
                scheduledAt = clock.now()
                    .toEpochMilliseconds() + request.initialDelay.inWholeMilliseconds,
                uniqueName = name
            )
        )
        db.taskDao().insertTags(request.tags.map { TaskTagEntity(request.id, it) })
    }

    private suspend fun insertWithDependency(
        request: TaskRequest,
        uniqueName: String,
        parentId: String
    ) {
        val task = request.toEntity(
            scheduledAt = clock.now()
                .toEpochMilliseconds() + request.initialDelay.inWholeMilliseconds,
            uniqueName = uniqueName
        )
        val tags = request.tags.map { TaskTagEntity(request.id, it) }
        val dependency = TaskDependencyEntity(
            parentTaskId = parentId,
            childTaskId = request.id,
            chainFailurePolicy = DependentTaskPolicy.STOP_ON_FAILURE
        )
        db.taskDao().insertChain(listOf(task), tags, listOf(dependency))
    }

    private suspend fun findLeafTask(rootId: String): TaskEntity {
        var current = db.taskDao().getTask(rootId) ?: return db.taskDao().getTask(rootId)!!
        while (true) {
            val children = db.taskDao().getDependenciesForParent(current.id)
            if (children.isEmpty()) break
            val child = db.taskDao().getTask(children.first().childTaskId) ?: break
            current = child
        }
        return current
    }

    private suspend fun cancelAndReplace(existingId: String) {
        val updated = db.taskDao().cancelTaskIfActive(existingId)
        if (updated > 0) {
            cancelDownstream(existingId)
        }
    }

    private suspend fun cancelDownstream(parentId: String) {
        db.taskDao().getDependenciesForParent(parentId).forEach { dep ->
            val updated = db.taskDao().cancelTaskIfActive(dep.childTaskId)
            if (updated > 0) {
                cancelDownstream(parentId = dep.childTaskId)
            }
        }
    }
}
