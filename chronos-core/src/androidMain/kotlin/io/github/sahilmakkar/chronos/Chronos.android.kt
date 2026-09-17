package io.github.sahilmakkar.chronos

import androidx.work.WorkManager
import io.github.sahilmakkar.chronos.db.executor.WorkConstraintVerifier
import io.github.sahilmakkar.chronos.executor.AndroidTaskExecutor
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
    val impl = buildAndroidChronos(support = support, registry = registry)
    ChronosRegistry.set(impl)
    return impl
}

internal fun buildAndroidChronos(
    support: PlatformSupport,
    registry: WorkerRegistry
): ChronosEngine {
    val appContext = support.context.applicationContext
    val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = createRoomDatabase(support = support)
    val workManager = WorkManager.getInstance(context = appContext)

    val taskExecutor = AndroidTaskExecutor(workManager = workManager, db = db)
    val coordinator = WorkChainCoordinator(
        db = db,
        taskExecutor = taskExecutor,
        constraintProvider = WorkConstraintVerifier()
    )
    val daemon = Daemon(coroutineScope = coroutineScope, coordinator = coordinator)
    val enqueuer = ChronosWorkEnqueuer(db = db, coroutineScope = coroutineScope, coordinator = coordinator)
    val canceller = ChronosWorkCanceller(workManager = workManager, db = db, coroutineScope = coroutineScope)

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