package io.github.sahilmakkar.chronos.scheduler

import io.github.sahilmakkar.chronos.engine.TaskCoordinator
import io.github.sahilmakkar.chronos.port.TaskEnqueuer
import io.github.sahilmakkar.chronos.storage.ChronosDatabase
import io.github.sahilmakkar.chronos.storage.TaskDependencyEntity
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
    private val onEnqueued: ((TaskRequest) -> Unit)? = null
) : TaskEnqueuer {

    override fun enqueue(request: TaskRequest) {
        coroutineScope.launch {
            db.taskDao().insertTask(
                request.toEntity(
                    scheduledAt = Clock.System.now()
                        .toEpochMilliseconds() + request.initialDelay.inWholeMilliseconds
                )
            )
            db.taskDao().insertTags(request.tags.map { TaskTagEntity(request.id, it) })
            onEnqueued?.invoke(request)
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
                        insertWithDependency(
                            request = request,
                            uniqueName = name,
                            parentId = existing.id
                        )
                        onEnqueued?.invoke(request)
                        return@launch
                    }
                }
            }
            insertUnique(request, name)
            onEnqueued?.invoke(request)
            coordinator.runPendingTasks()
        }
    }

    override fun enqueueUniquePeriodicTask(
        name: String,
        policy: UniqueTaskPolicy,
        request: PeriodicTaskRequest
    ) {
        coroutineScope.launch {
            val existing = db.taskDao().getTaskByName(name)
            if (existing != null) {
                when (policy) {
                    UniqueTaskPolicy.KEEP -> return@launch
                    UniqueTaskPolicy.REPLACE -> cancelAndReplace(existing.id)
                    UniqueTaskPolicy.APPEND -> throw IllegalArgumentException(
                        "APPEND policy is not supported for unique periodic work."
                    )
                }
            }
            insertUnique(request, name)
            onEnqueued?.invoke(request)
            coordinator.runPendingTasks()
        }
    }

    override fun enqueue(chain: TaskChain) {
        if (chain.requests.isEmpty()) return
        coroutineScope.launch {
            val nowMs = Clock.System.now().toEpochMilliseconds()
            val dependencies = mutableListOf<TaskDependencyEntity>()
            chain.requests.forEachIndexed { i, req ->
                db.taskDao().insertTask(
                    req.toEntity(
                        scheduledAt = nowMs + req.initialDelay.inWholeMilliseconds,
                        chainId = chain.requests.first().id,
                        chainOrder = i
                    )
                )
                db.taskDao().insertTags(req.tags.map { TaskTagEntity(req.id, it) })
                if (i > 0) {
                    dependencies.add(
                        TaskDependencyEntity(
                            parentTaskId = chain.requests[i - 1].id,
                            childTaskId = req.id,
                            chainFailurePolicy = chain.failurePolicies[i - 1]
                        )
                    )
                }
                onEnqueued?.invoke(req)
            }
            db.taskDao().insertDependencies(dependencies)
            coordinator.runPendingTasks()
        }
    }

    private suspend fun insertUnique(request: TaskRequest, name: String) {
        db.taskDao().insertTask(
            request.toEntity(
                scheduledAt = Clock.System.now().toEpochMilliseconds() + request.initialDelay.inWholeMilliseconds,
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
        db.taskDao().insertTask(
            request.toEntity(
                scheduledAt = Clock.System.now().toEpochMilliseconds() + request.initialDelay.inWholeMilliseconds,
                uniqueName = uniqueName
            )
        )
        db.taskDao().insertTags(request.tags.map { TaskTagEntity(request.id, it) })
        db.taskDao().insertDependencies(
            listOf(
                TaskDependencyEntity(
                    parentTaskId = parentId,
                    childTaskId = request.id,
                    chainFailurePolicy = DependentTaskPolicy.STOP_ON_FAILURE
                )
            )
        )
    }

    private suspend fun cancelAndReplace(existingId: String) {
        db.taskDao().updateTaskState(id = existingId, state = TaskState.CANCELLED)
        cancelDownstream(existingId)
    }

    private suspend fun cancelDownstream(parentId: String) {
        db.taskDao().getDependenciesForParent(parentId).forEach { dep ->
            db.taskDao().updateTaskState(id = dep.childTaskId, state = TaskState.CANCELLED)
            cancelDownstream(parentId = dep.childTaskId)
        }
    }
}

@Deprecated("Use ChronosTaskEnqueuer instead", ReplaceWith("ChronosTaskEnqueuer"))
internal typealias ChronosWorkEnqueuer = ChronosTaskEnqueuer
