@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.task.model.TaskInfo
import io.github.sahilmakkar.chronos.data.mapper.toWorkInfo
import io.github.sahilmakkar.chronos.db.WorkDatabase
import io.github.sahilmakkar.chronos.db.createRoomDatabase
import io.github.sahilmakkar.chronos.executor.IosBackgroundTaskScheduler
import io.github.sahilmakkar.chronos.executor.WorkChainCoordinator
import io.github.sahilmakkar.chronos.executor.WorkConstraintVerifier
import io.github.sahilmakkar.chronos.executor.WorkTaskExecutor
import io.github.sahilmakkar.chronos.kmpWorker.KmpWorkerRegistry
import io.github.sahilmakkar.chronos.task.policy.UniqueTaskPolicy
import io.github.sahilmakkar.chronos.task.TaskChain
import io.github.sahilmakkar.chronos.network.NetworkChecker
import io.github.sahilmakkar.chronos.scheduler.PlatformScheduler
import io.github.sahilmakkar.chronos.scheduler.canceller.WorkCanceller
import io.github.sahilmakkar.chronos.scheduler.workEnqueuer.WorkEnqueuer
import io.github.sahilmakkar.chronos.scheduler.WorkObserver
import io.github.sahilmakkar.chronos.utils.KmpClock
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import io.github.sahilmakkar.chronos.task.request.PeriodicTaskRequest
import io.github.sahilmakkar.chronos.task.request.TaskRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

actual class Chronos actual constructor(
    actual val registry: KmpWorkerRegistry,
    actual val clock: KmpClock
) : WorkEnqueuer,
    WorkCanceller,
    WorkObserver,
    PlatformScheduler,
    AutoCloseable {

    private val db: WorkDatabase = createRoomDatabase()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    actual val networkChecker: NetworkChecker by lazy { IosNetworkChecker() }

    private val canceller by lazy {
        io.github.sahilmakkar.chronos.scheduler.canceller.ChronosWorkCanceller(
            db,
            coroutineScope
        )
    }

    private val constraintVerifier by lazy {
        WorkConstraintVerifier(networkChecker, clock)
    }

    private val taskExecutor by lazy {
        WorkTaskExecutor(db = db, registry = registry, clock = clock)
    }

    private val coordinator by lazy {
        WorkChainCoordinator(
            db = db,
            clock = clock,
            constraintVerifier = constraintVerifier,
            taskExecutor = taskExecutor
        ).also { taskExecutor.coordinator = it }
    }

    private val bgTaskScheduler by lazy {
        IosBackgroundTaskScheduler(
            coroutineScope = coroutineScope,
            onTaskFired = { coordinator.runPendingTasks() },
            onExpired = { coordinator.sweepOrphans() }
        )
    }

    private val enqueuer by lazy {
        io.github.sahilmakkar.chronos.scheduler.workEnqueuer.ChronosWorkEnqueuer(
            db = db,
            clock = clock,
            coroutineScope = coroutineScope,
            coordinator = coordinator,
            bgTaskScheduler = bgTaskScheduler
        )
    }

    actual companion object {}

    init {
        Chronos.initialize(this)
        coroutineScope.launch {
            coordinator.sweepOrphans()
            coordinator.sweepChainRecovery()
            coordinator.runPendingTasks()
        }
        bgTaskScheduler.register()
    }

    // --- WorkEnqueuer — delegated to ChronosWorkEnqueuer ---

    actual override fun enqueue(request: TaskRequest) = enqueuer.enqueue(request)

    actual override fun enqueueUniqueWork(
        name: String,
        policy: UniqueTaskPolicy,
        request: OneTimeTaskRequest
    ) = enqueuer.enqueueUniqueWork(name, policy, request)

    actual override fun enqueueUniquePeriodicWork(
        name: String,
        policy: UniqueTaskPolicy,
        request: PeriodicTaskRequest
    ) = enqueuer.enqueueUniquePeriodicWork(name, policy, request)

    actual override fun enqueue(chain: TaskChain) = enqueuer.enqueue(chain)

    // --- WorkCanceller — delegated to ChronosWorkCanceller ---

    actual override fun cancelWorkById(id: String) = canceller.cancelWorkById(id)
    actual override fun cancelWorkByTag(tag: String) = canceller.cancelWorkByTag(tag)
    actual override fun cancelAllWork() = canceller.cancelAllWork()

    // --- WorkObserver ---

    actual override fun getWorkInfoFlow(id: String): Flow<TaskInfo?> =
        db.workDao().observeWorkRequest(id).map { it?.toWorkInfo() }

    actual override fun getWorkInfosByTagFlow(tag: String): Flow<List<TaskInfo>> =
        db.workDao().observeWorkRequestsByTag(tag).map { list -> list.map { it.toWorkInfo() } }

    // --- PlatformScheduler ---

    actual override suspend fun runPendingTasksNow() = coordinator.runPendingTasks()

    // --- AutoCloseable ---

    actual override fun close() {
        coroutineScope.cancel()
        db.close()
    }
}