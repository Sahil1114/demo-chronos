package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.constraints.IosConstraintVerifier
import io.github.sahilmakkar.chronos.engine.ChronosEngine
import io.github.sahilmakkar.chronos.engine.Daemon
import io.github.sahilmakkar.chronos.engine.TaskCoordinator
import io.github.sahilmakkar.chronos.executor.InProcessTaskExecutor
import io.github.sahilmakkar.chronos.executor.IosBackgroundTaskScheduler
import io.github.sahilmakkar.chronos.scheduler.ChronosTaskCanceller
import io.github.sahilmakkar.chronos.scheduler.ChronosTaskEnqueuer
import io.github.sahilmakkar.chronos.storage.RoomTaskStore
import io.github.sahilmakkar.chronos.storage.createRoomDatabase
import io.github.sahilmakkar.chronos.worker.WorkerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

actual fun Chronos.Companion.initialize(
    support: PlatformSupport,
    registry: WorkerRegistry
): Chronos {
    val impl = buildIosChronos(support = support, registry = registry)
    ChronosRegistry.set(impl)
    return impl
}

internal fun buildIosChronos(support: PlatformSupport, registry: WorkerRegistry): ChronosEngine {
    val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = createRoomDatabase(support)
    val networkChecker = IosNetworkChecker()

    val taskExecutor = InProcessTaskExecutor(db = db, registry = registry)
    val coordinator = TaskCoordinator(
        db = db,
        taskExecutor = taskExecutor,
        constraintProvider = IosConstraintVerifier(networkChecker)
    )
    val bgTaskScheduler by lazy {
        IosBackgroundTaskScheduler(
            coroutineScope = coroutineScope,
            onTaskFired = { coordinator.runPendingTasks() },
            onExpired = { coordinator.sweepOrphans() }
        )
    }

    val daemon = Daemon(
        coroutineScope = coroutineScope,
        coordinator = coordinator,
        scheduler = bgTaskScheduler
    )
    val enqueuer = ChronosTaskEnqueuer(
        db = db,
        coroutineScope = coroutineScope,
        coordinator = coordinator,
        onEnqueued = { bgTaskScheduler.scheduleForRequest(it) }
    )
    val canceller = ChronosTaskCanceller(db = db, coroutineScope = coroutineScope)
    val taskStore = RoomTaskStore(db.taskDao())

    return ChronosEngine(
        registry = registry,
        coroutineScope = coroutineScope,
        coordinator = coordinator,
        daemon = daemon,
        enqueuer = enqueuer,
        canceller = canceller,
        taskStore = taskStore,
        db = db
    )
}
