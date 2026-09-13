@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.db.executor.WorkTaskExecutor
import io.github.sahilmakkar.chronos.executor.IosBackgroundTaskScheduler
import io.github.sahilmakkar.chronos.executor.WorkConstraintVerifier
import io.github.sahilmakkar.chronos.network.NetworkChecker
import io.github.sahilmakkar.chronos.roomDb.WorkDatabase
import io.github.sahilmakkar.chronos.roomDb.createRoomDatabase
import io.github.sahilmakkar.chronos.roomDb.executor.WorkChainCoordinator
import io.github.sahilmakkar.chronos.scheduler.PlatformScheduler
import io.github.sahilmakkar.chronos.scheduler.WorkObserver
import io.github.sahilmakkar.chronos.scheduler.canceller.ChronosWorkCanceller
import io.github.sahilmakkar.chronos.scheduler.canceller.WorkCanceller
import io.github.sahilmakkar.chronos.scheduler.workEnqueuer.ChronosWorkEnqueuer
import io.github.sahilmakkar.chronos.scheduler.workEnqueuer.WorkEnqueuer
import io.github.sahilmakkar.chronos.task.TaskChain
import io.github.sahilmakkar.chronos.task.model.TaskInfo
import io.github.sahilmakkar.chronos.task.policy.UniqueTaskPolicy
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import io.github.sahilmakkar.chronos.task.request.PeriodicTaskRequest
import io.github.sahilmakkar.chronos.task.request.TaskRequest
import io.github.sahilmakkar.chronos.worker.ChronosCore
import io.github.sahilmakkar.chronos.worker.WorkerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow

actual class Chronos actual constructor(
    actual val registry: WorkerRegistry,
) : WorkEnqueuer,
    WorkCanceller,
    WorkObserver,
    PlatformScheduler,
    AutoCloseable {

    private val db: WorkDatabase = createRoomDatabase()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    actual val networkChecker: NetworkChecker by lazy { IosNetworkChecker() }

    private val canceller by lazy {
        ChronosWorkCanceller(db, coroutineScope)
    }

    private val constraintVerifier by lazy {
        WorkConstraintVerifier(networkChecker)
    }

    private val taskExecutor by lazy {
        WorkTaskExecutor(db = db, registry = registry)
    }

    private val coordinator by lazy {
        WorkChainCoordinator(
            db = db,
            constraintProvider = constraintVerifier,
            taskExecutor = taskExecutor
        )
    }

    private val bgTaskScheduler by lazy {
        IosBackgroundTaskScheduler(
            coroutineScope = coroutineScope,
            onTaskFired = { coordinator.runPendingTasks() },
            onExpired = { coordinator.sweepOrphans() }
        )
    }

    private val daemon by lazy {
        Daemon(
            coroutineScope = coroutineScope,
            coordinator = coordinator,
            scheduler = bgTaskScheduler
        )
    }

    private val enqueuer by lazy {
        ChronosWorkEnqueuer(
            db = db,
            coroutineScope = coroutineScope,
            coordinator = coordinator,
            bgTaskScheduler = bgTaskScheduler
        )
    }

    // All shared, non-platform-specific behaviour lives in ChronosCore.
    // This class's only job past this point is: build iOS-flavoured
    // dependencies above (including the background task scheduler), then
    // hand them off.
    private val core by lazy {
        ChronosCore(
            db = db,
            coroutineScope = coroutineScope,
            enqueuer = enqueuer,
            canceller = canceller,
            coordinator = coordinator,
            daemon = daemon
        )
    }

    actual companion object {}

    init {
        Chronos.initialize(this)
        daemon.start()
    }

    // --- WorkEnqueuer — delegated to ChronosCore ---

    actual override fun enqueue(request: TaskRequest) = core.enqueue(request)

    actual override fun enqueueUniqueWork(
        name: String,
        policy: UniqueTaskPolicy,
        request: OneTimeTaskRequest
    ) = core.enqueueUniqueWork(name, policy, request)

    actual override fun enqueueUniquePeriodicWork(
        name: String,
        policy: UniqueTaskPolicy,
        request: PeriodicTaskRequest
    ) = core.enqueueUniquePeriodicWork(name, policy, request)

    actual override fun enqueue(chain: TaskChain) = core.enqueue(chain)

    // --- WorkCanceller — delegated to ChronosCore ---

    actual override fun cancelWorkById(id: String) = core.cancelWorkById(id)
    actual override fun cancelWorkByTag(tag: String) = core.cancelWorkByTag(tag)
    actual override fun cancelAllWork() = core.cancelAllWork()

    // --- WorkObserver ---

    actual override fun getWorkInfoFlow(id: String): Flow<TaskInfo?> =
        core.getWorkInfoFlow(id)

    actual override fun getWorkInfosByTagFlow(tag: String): Flow<List<TaskInfo>> =
        core.getWorkInfosByTagFlow(tag)

    // --- PlatformScheduler ---

    actual override suspend fun runPendingTasksNow() = core.runPendingTasksNow()

    // --- AutoCloseable ---

    actual override fun close() = core.close()
}