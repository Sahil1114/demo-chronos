package io.github.sahilmakkar.chronos

import android.content.Context
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

fun Chronos.Companion.initialize(context: Context, registry: WorkerRegistry): Chronos {
    val impl = buildAndroidChronos(context, registry)
    ChronosRegistry.set(impl)
    return impl
}

internal fun buildAndroidChronos(context: Context, registry: WorkerRegistry): ChronosEngine {
    val appContext = context.applicationContext
    val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = createRoomDatabase()
    val workManager = WorkManager.getInstance(appContext)

    val taskExecutor = AndroidTaskExecutor(workManager = workManager, db = db)
    val coordinator = WorkChainCoordinator(
        db = db,
        taskExecutor = taskExecutor,
        constraintProvider = WorkConstraintVerifier()
    )
    val daemon = Daemon(coroutineScope = coroutineScope, coordinator = coordinator)
    val enqueuer =
        ChronosWorkEnqueuer(db = db, coroutineScope = coroutineScope, coordinator = coordinator)
    val canceller =
        ChronosWorkCanceller(workManager = workManager, db = db, coroutineScope = coroutineScope)

    return ChronosEngine(registry, coroutineScope, db, coordinator, daemon, enqueuer, canceller)
}