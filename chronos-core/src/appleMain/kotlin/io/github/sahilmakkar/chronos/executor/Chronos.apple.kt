package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.db.executor.WorkConstraintVerifier
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

fun Chronos.Companion.initialize(registry: WorkerRegistry): Chronos {
    val impl = buildIosChronos(registry)
    ChronosRegistry.set(impl)
    return impl
}

internal fun buildIosChronos(registry: WorkerRegistry): ChronosEngine {
    val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = createRoomDatabase()
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

    return ChronosEngine(registry, coroutineScope, db, coordinator, daemon, enqueuer, canceller)
}