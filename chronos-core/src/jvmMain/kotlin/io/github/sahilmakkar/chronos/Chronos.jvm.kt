package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.db.executor.WorkConstraintVerifier
import io.github.sahilmakkar.chronos.db.executor.WorkTaskExecutor
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
    val impl = buildJvmChronos(support = support, registry = registry)
    ChronosRegistry.set(impl)
    return impl
}

internal fun buildJvmChronos(support: PlatformSupport, registry: WorkerRegistry): ChronosEngine {
    val db = createRoomDatabase(support)
    val coroutineScope = CoroutineScope(context = SupervisorJob() + Dispatchers.Default)
    val networkChecker = JvmNetworkChecker()

    val taskExecutor = WorkTaskExecutor(db = db, registry = registry)
    val constraintVerifier = WorkConstraintVerifier(networkChecker = networkChecker)
    val coordinator = WorkChainCoordinator(
        db = db,
        constraintProvider = constraintVerifier,
        taskExecutor = taskExecutor
    )
    val daemon = Daemon(coroutineScope = coroutineScope, coordinator = coordinator)
    val enqueuer =
        ChronosWorkEnqueuer(db = db, coroutineScope = coroutineScope, coordinator = coordinator)
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