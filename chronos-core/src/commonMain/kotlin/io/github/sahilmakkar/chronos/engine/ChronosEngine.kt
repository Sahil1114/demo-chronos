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
import io.github.sahilmakkar.chronos.scheduler.WakeScheduler
import io.github.sahilmakkar.chronos.worker.WorkerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking

/**
 * Production implementation of [Chronos] unifying scheduling, persistence, background observation, and lifecycle management.
 *
 * Coordinates internal subsystems:
 * - [TaskEnqueuer] for transactional task and chain insertions.
 * - [TaskCanceller] for active task state revocation and downstream dependency cancellation.
 * - [TaskStore] for Room database state observation flows.
 * - [TaskCoordinator] for concurrent task claiming and execution.
 * - [Daemon] for driving the background event dispatch loop.
 *
 * ### Lifecycle and Shutdown:
 * Implements [AutoCloseable]. When [close] is invoked, resources are shut down in strict order:
 * 1. The background [Daemon] loop is stopped.
 * 2. The internal [CoroutineScope] is cancelled, terminating active child jobs.
 * 3. The [onClose] callback is invoked to close the underlying Room database.
 */
internal class ChronosEngine(
    override val registry: WorkerRegistry,
    private val coroutineScope: CoroutineScope,
    private val coordinator: TaskCoordinator,
    private val daemon: Daemon,
    private val enqueuer: TaskEnqueuer,
    private val canceller: TaskCanceller,
    private val taskStore: TaskStore,
    private val onClose: () -> Unit = {},
    val wakeScheduler: WakeScheduler? = null
) : Chronos {

    init {
        daemon.start()
    }

    override fun enqueueTask(
        name: String,
        policy: UniqueTaskPolicy,
        request: OneTimeTaskRequest
    ) = enqueuer.enqueueTask(name, policy, request)

    override fun enqueuePeriodicTask(
        name: String,
        policy: UniqueTaskPolicy,
        request: PeriodicTaskRequest
    ) = enqueuer.enqueuePeriodicTask(name, policy, request)

    override fun enqueue(chain: TaskChain) = enqueuer.enqueue(chain)

    override fun cancelTaskById(id: String) = canceller.cancelTaskById(id)
    override fun cancelTaskByTag(tag: String) = canceller.cancelTaskByTag(tag)
    override fun cancelAllTasks() = canceller.cancelAllTasks()

    override fun observeTask(id: String): Flow<TaskInfo?> =
        taskStore.observeTask(id)

    override fun observeTasksByTag(tag: String): Flow<List<TaskInfo>> =
        taskStore.observeTasksByTag(tag)

    override suspend fun runPendingTasksNow() {
        coordinator.runPendingTasks()
        wakeScheduler?.scheduleNextWake()
    }

    /**
     * Shuts down the engine: stops daemon loop, joins pending coroutine jobs, and closes database.
     */
    override fun close() {
        daemon.stop()
        val job = coroutineScope.coroutineContext[Job]
        job?.cancel()
        runBlocking {
            job?.children?.toList()?.forEach { it.join() }
        }
        onClose()
    }
}
