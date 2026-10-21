package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.executor.WorkConstraintVerifier
import io.github.sahilmakkar.chronos.db.executor.WorkTaskExecutor
import io.github.sahilmakkar.chronos.executor.IosBackgroundTaskScheduler
import io.github.sahilmakkar.chronos.roomDb.createRoomDatabase
import io.github.sahilmakkar.chronos.roomDb.executor.WorkChainCoordinator
import io.github.sahilmakkar.chronos.scheduler.canceller.ChronosWorkCanceller
import io.github.sahilmakkar.chronos.scheduler.workEnqueuer.ChronosWorkEnqueuer
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


    val taskExecutor = WorkTaskExecutor(db = db, registry = registry)
    val coordinator = WorkChainCoordinator(
        db = db,
        taskExecutor = taskExecutor,
        constraintProvider = WorkConstraintVerifier(networkChecker)
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
    val enqueuer =
        ChronosWorkEnqueuer(
            db = db,
            coroutineScope = coroutineScope,
            coordinator = coordinator,
            bgTaskScheduler
        )
    val canceller = ChronosWorkCanceller(db = db, scope = coroutineScope)

    return ChronosEngine(
        registry = registry,
        coroutineScope = coroutineScope,
        db = db,
        coordinator = coordinator,
        daemon = daemon,
        enqueuer = enqueuer,
        canceller = canceller
    )
}