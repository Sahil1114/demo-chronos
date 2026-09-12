package io.github.sahilmakkar.chronos.scheduler.workEnqueuer

import io.github.sahilmakkar.chronos.roomDb.toEntity
import io.github.sahilmakkar.chronos.roomDb.WorkDatabase
import io.github.sahilmakkar.chronos.roomDb.WorkDependencyEntity
import io.github.sahilmakkar.chronos.roomDb.WorkTagEntity
import io.github.sahilmakkar.chronos.executor.IosBackgroundTaskScheduler
import io.github.sahilmakkar.chronos.roomDb.executor.WorkChainCoordinator
import io.github.sahilmakkar.chronos.task.policy.UniqueTaskPolicy
import io.github.sahilmakkar.chronos.task.TaskChain
import io.github.sahilmakkar.chronos.task.TaskState
import io.github.sahilmakkar.chronos.task.policy.DependentTaskPolicy
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import io.github.sahilmakkar.chronos.task.request.PeriodicTaskRequest
import io.github.sahilmakkar.chronos.task.request.TaskRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Clock

actual class ChronosWorkEnqueuer(
    private val db: WorkDatabase,
    private val coroutineScope: CoroutineScope,
    private val coordinator: WorkChainCoordinator,
    private val bgTaskScheduler: IosBackgroundTaskScheduler
) : WorkEnqueuer {

    actual override fun enqueue(request: TaskRequest) {
        coroutineScope.launch {
            db.workDao().insertWorkRequest(
                request.toEntity(Clock.System.now().toEpochMilliseconds() + request.initialDelay.inWholeMilliseconds)
            )
            db.workDao().insertTags(request.tags.map { WorkTagEntity(request.id, it) })
            bgTaskScheduler.scheduleForRequest(request)
            coordinator.runPendingTasks()
        }
    }

    actual override fun enqueueUniqueWork(
        name: String,
        policy: UniqueTaskPolicy,
        request: OneTimeTaskRequest
    ) {
        coroutineScope.launch {
            val existing = db.workDao().getWorkRequestByName(name)
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
                        bgTaskScheduler.scheduleForRequest(request)
                        return@launch
                    }
                }
            }
            insertUnique(request, name)
            bgTaskScheduler.scheduleForRequest(request)
            coordinator.runPendingTasks()
        }
    }

    actual override fun enqueueUniquePeriodicWork(
        name: String,
        policy: UniqueTaskPolicy,
        request: PeriodicTaskRequest
    ) {
        coroutineScope.launch {
            val existing = db.workDao().getWorkRequestByName(name)
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
            bgTaskScheduler.scheduleForRequest(request)
            coordinator.runPendingTasks()
        }
    }

    actual override fun enqueue(chain: TaskChain) {
        if (chain.requests.isEmpty()) return
        coroutineScope.launch {
            val nowMs = Clock.System.now().toEpochMilliseconds()
            val dependencies = mutableListOf<WorkDependencyEntity>()
            chain.requests.forEachIndexed { i, req ->
                db.workDao().insertWorkRequest(
                    req.toEntity(
                        scheduledAt = nowMs + req.initialDelay.inWholeMilliseconds,
                        chainId = chain.requests.first().id,
                        chainOrder = i
                    )
                )
                db.workDao().insertTags(req.tags.map { WorkTagEntity(req.id, it) })
                if (i > 0) {
                    dependencies.add(
                        WorkDependencyEntity(
                            parentWorkId = chain.requests[i - 1].id,
                            childWorkId = req.id,
                            chainFailurePolicy = chain.failurePolicies[i - 1]
                        )
                    )
                }
                bgTaskScheduler.scheduleForRequest(req)
            }
            db.workDao().insertDependencies(dependencies)
            coordinator.runPendingTasks()
        }
    }

    // --- Helpers ---

    private suspend fun cancelAndReplace(existingId: String) {
        db.workDao().updateWorkState(id = existingId, state = TaskState.CANCELLED)
    }

    private suspend fun insertUnique(request: TaskRequest, uniqueName: String) {
        db.workDao().insertWorkRequest(
            request.toEntity(
                scheduledAt = Clock.System.now().toEpochMilliseconds() + request.initialDelay.inWholeMilliseconds,
                uniqueName = uniqueName
            )
        )
        db.workDao().insertTags(request.tags.map { WorkTagEntity(request.id, it) })
    }

    private suspend fun insertWithDependency(
        request: OneTimeTaskRequest,
        uniqueName: String,
        parentId: String
    ) {
        db.workDao().insertWorkRequest(
            request.toEntity(
                scheduledAt = Clock.System.now().toEpochMilliseconds() + request.initialDelay.inWholeMilliseconds,
                uniqueName = uniqueName
            )
        )
        db.workDao().insertTags(request.tags.map { WorkTagEntity(request.id, it) })
        db.workDao().insertDependencies(listOf(
            WorkDependencyEntity(
                parentWorkId = parentId,
                childWorkId = request.id,
                chainFailurePolicy = DependentTaskPolicy.STOP_ON_FAILURE
            )
        ))
    }
}