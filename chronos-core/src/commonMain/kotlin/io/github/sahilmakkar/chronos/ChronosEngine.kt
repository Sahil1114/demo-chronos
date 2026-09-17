package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.roomDb.WorkDatabase
import io.github.sahilmakkar.chronos.roomDb.executor.WorkChainCoordinator
import io.github.sahilmakkar.chronos.roomDb.toWorkInfo
import io.github.sahilmakkar.chronos.scheduler.canceller.WorkCanceller
import io.github.sahilmakkar.chronos.scheduler.workEnqueuer.WorkEnqueuer
import io.github.sahilmakkar.chronos.task.TaskChain
import io.github.sahilmakkar.chronos.task.model.TaskInfo
import io.github.sahilmakkar.chronos.task.policy.UniqueTaskPolicy
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import io.github.sahilmakkar.chronos.task.request.PeriodicTaskRequest
import io.github.sahilmakkar.chronos.task.request.TaskRequest
import io.github.sahilmakkar.chronos.worker.WorkerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class ChronosEngine(
    override val registry: WorkerRegistry,
    private val coroutineScope: CoroutineScope,
    private val db: WorkDatabase,
    private val coordinator: WorkChainCoordinator,
    private val daemon: Daemon,
    private val enqueuer: WorkEnqueuer,
    private val canceller: WorkCanceller,
) : Chronos {

    init {
        daemon.start()
    }

    override fun enqueue(request: TaskRequest) = enqueuer.enqueue(request)
    override fun enqueueUniqueWork(
        name: String,
        policy: UniqueTaskPolicy,
        request: OneTimeTaskRequest
    ) =
        enqueuer.enqueueUniqueWork(name, policy, request)

    override fun enqueueUniquePeriodicWork(
        name: String,
        policy: UniqueTaskPolicy,
        request: PeriodicTaskRequest
    ) =
        enqueuer.enqueueUniquePeriodicWork(name, policy, request)

    override fun enqueue(chain: TaskChain) = enqueuer.enqueue(chain)

    override fun cancelWorkById(id: String) = canceller.cancelWorkById(id)
    override fun cancelWorkByTag(tag: String) = canceller.cancelWorkByTag(tag)
    override fun cancelAllWork() = canceller.cancelAllWork()

    override fun getWorkInfoFlow(id: String): Flow<TaskInfo?> =
        db.workDao().observeWorkRequest(id).map { it?.toWorkInfo() }

    override fun getWorkInfosByTagFlow(tag: String): Flow<List<TaskInfo>> =
        db.workDao().observeWorkRequestsByTag(tag).map { list -> list.map { it.toWorkInfo() } }

    override suspend fun runPendingTasksNow() = coordinator.runPendingTasks()

    override fun close() {
        daemon.stop()
        coroutineScope.cancel()
        db.close()
    }
}