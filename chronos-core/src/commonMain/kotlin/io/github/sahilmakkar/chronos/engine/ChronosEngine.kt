package io.github.sahilmakkar.chronos.engine

import io.github.sahilmakkar.chronos.Chronos
import io.github.sahilmakkar.chronos.port.TaskCanceller
import io.github.sahilmakkar.chronos.port.TaskEnqueuer
import io.github.sahilmakkar.chronos.port.TaskStore
import io.github.sahilmakkar.chronos.storage.ChronosDatabase
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

internal class ChronosEngine(
    override val registry: WorkerRegistry,
    private val coroutineScope: CoroutineScope,
    private val coordinator: TaskCoordinator,
    private val daemon: Daemon,
    private val enqueuer: TaskEnqueuer,
    private val canceller: TaskCanceller,
    private val taskStore: TaskStore,
    private val db: ChronosDatabase? = null
) : Chronos {

    init {
        daemon.start()
    }

    override fun enqueue(request: TaskRequest) = enqueuer.enqueue(request)
    override fun enqueueUniqueTask(
        name: String,
        policy: UniqueTaskPolicy,
        request: OneTimeTaskRequest
    ) = enqueuer.enqueueUniqueTask(name, policy, request)

    override fun enqueueUniquePeriodicTask(
        name: String,
        policy: UniqueTaskPolicy,
        request: PeriodicTaskRequest
    ) = enqueuer.enqueueUniquePeriodicTask(name, policy, request)

    override fun enqueue(chain: TaskChain) = enqueuer.enqueue(chain)

    override fun cancelTaskById(id: String) = canceller.cancelTaskById(id)
    override fun cancelTaskByTag(tag: String) = canceller.cancelTaskByTag(tag)
    override fun cancelAllTasks() = canceller.cancelAllTasks()

    override fun observeTask(id: String): Flow<TaskInfo?> =
        taskStore.observeTask(id)

    override fun observeTasksByTag(tag: String): Flow<List<TaskInfo>> =
        taskStore.observeTasksByTag(tag)

    override suspend fun runPendingTasksNow() = coordinator.runPendingTasks()

    override fun close() {
        daemon.stop()
        coroutineScope.cancel()
        db?.close()
    }
}
