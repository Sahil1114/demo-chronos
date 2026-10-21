package io.github.sahilmakkar.chronos

import androidx.work.WorkManager
import io.github.sahilmakkar.chronos.constraints.AndroidConstraintVerifier
import io.github.sahilmakkar.chronos.engine.ChronosEngine
import io.github.sahilmakkar.chronos.engine.Daemon
import io.github.sahilmakkar.chronos.engine.TaskCoordinator
import io.github.sahilmakkar.chronos.executor.AndroidTaskExecutor
import io.github.sahilmakkar.chronos.scheduler.ChronosTaskCanceller
import io.github.sahilmakkar.chronos.scheduler.ChronosTaskEnqueuer
import io.github.sahilmakkar.chronos.storage.RoomTaskStore
import io.github.sahilmakkar.chronos.storage.createRoomDatabase
import io.github.sahilmakkar.chronos.worker.WorkerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.UUID

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
    val coordinator = TaskCoordinator(
        db = db,
        taskExecutor = taskExecutor,
        constraintProvider = AndroidConstraintVerifier()
    )
    val daemon = Daemon(coroutineScope = coroutineScope, coordinator = coordinator)
    val enqueuer = ChronosTaskEnqueuer(db = db, coroutineScope = coroutineScope, coordinator = coordinator)
    val canceller = ChronosTaskCanceller(
        db = db,
        coroutineScope = coroutineScope,
        onCancelById = { workManager.cancelWorkById(UUID.fromString(it)) },
        onCancelByTag = { workManager.cancelAllWorkByTag(it) },
        onCancelAll = { workManager.cancelAllWork() }
    )
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
