package io.github.sahilmakkar.chronos.scheduler

import io.github.sahilmakkar.chronos.engine.TaskCoordinator
import io.github.sahilmakkar.chronos.port.TaskEnqueuer
import io.github.sahilmakkar.chronos.storage.ChronosDatabase
import io.github.sahilmakkar.chronos.storage.TaskDependencyEntity
import io.github.sahilmakkar.chronos.storage.TaskEntity
import io.github.sahilmakkar.chronos.storage.TaskTagEntity
import io.github.sahilmakkar.chronos.storage.mapper.toEntity
import io.github.sahilmakkar.chronos.task.TaskChain
import io.github.sahilmakkar.chronos.task.TaskState
import io.github.sahilmakkar.chronos.task.policy.DependentTaskPolicy
import io.github.sahilmakkar.chronos.task.policy.UniqueTaskPolicy
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import io.github.sahilmakkar.chronos.task.request.PeriodicTaskRequest
import io.github.sahilmakkar.chronos.task.request.TaskRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Clock

internal class ChronosTaskEnqueuer(
    private val db: ChronosDatabase,
    private val coroutineScope: CoroutineScope,
    private val coordinator: TaskCoordinator,
    private val clock: Clock = Clock.System,
    private val onEnqueued: ((TaskRequest) -> Unit)? = null,
    private val wakeScheduler: WakeScheduler? = null
) : TaskEnqueuer {

    override fun enqueue(request: TaskRequest) {
        coroutineScope.launch {
            db.taskDao().insertTask(
                request.toEntity(
                    scheduledAt = clock.now()
                        .toEpochMilliseconds() + request.initialDelay.inWholeMilliseconds
                )
            )
            db.taskDao().insertTags(request.tags.map { TaskTagEntity(request.id, it) })
            onEnqueued?.invoke(request)
            wakeScheduler?.scheduleNextWake()
            coordinator.runPendingTasks()
        }
    }

    override fun enqueueUniqueTask(
        name: String,
        policy: UniqueTaskPolicy,
        request: OneTimeTaskRequest
    ) {
        coroutineScope.launch {
            val existing = db.taskDao().getTaskByName(name)
            if (existing != null) {
                when (policy) {
                    UniqueTaskPolicy.KEEP -> return@launch
                    UniqueTaskPolicy.REPLACE -> cancelAndReplace(existing.id)
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

    override fun enqueueUniquePeriodicTask(
        name: String,
        policy: UniqueTaskPolicy,
        request: PeriodicTaskRequest
    ) {
        require(policy != UniqueTaskPolicy.APPEND) {
            "APPEND policy is not supported for unique periodic work."
        }
        coroutineScope.launch {
            val existing = db.taskDao().getTaskByName(name)
            if (existing != null) {
                when (policy) {
                    UniqueTaskPolicy.KEEP -> return@launch
                    UniqueTaskPolicy.REPLACE -> cancelAndReplace(existing.id)
                    UniqueTaskPolicy.APPEND -> return@launch
                }
            }
            insertUnique(request, name)
            onEnqueued?.invoke(request)
            wakeScheduler?.scheduleNextWake()
            coordinator.runPendingTasks()
        }
    }

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
                        chainId = chain.requests.first().id,
                        chainOrder = i
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
                scheduledAt = clock.now().toEpochMilliseconds() + request.initialDelay.inWholeMilliseconds,
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
            scheduledAt = clock.now().toEpochMilliseconds() + request.initialDelay.inWholeMilliseconds,
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
