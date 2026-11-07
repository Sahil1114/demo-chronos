package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.constraints.AndroidConstraintVerifier
import io.github.sahilmakkar.chronos.engine.ChronosEngine
import io.github.sahilmakkar.chronos.engine.Daemon
import io.github.sahilmakkar.chronos.engine.TaskCoordinator
import io.github.sahilmakkar.chronos.executor.InProcessTaskExecutor
import io.github.sahilmakkar.chronos.scheduler.ChronosTaskCanceller
import io.github.sahilmakkar.chronos.scheduler.ChronosTaskEnqueuer
import io.github.sahilmakkar.chronos.scheduler.ChronosWakeScheduler
import io.github.sahilmakkar.chronos.storage.RoomTaskStore
import io.github.sahilmakkar.chronos.storage.createRoomDatabase
import io.github.sahilmakkar.chronos.worker.WorkerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Android platform entry point implementing [Chronos.Companion.initialize].
 *
 * Initializes the Room SQLite database on disk (`chronos_work.db`), configures [ChronosWakeScheduler]
 * backed by Android WorkManager for Option B wake worker triggers, initializes [AndroidConstraintVerifier],
 * and sets the singleton instance accessible via [Chronos.Companion.instance].
 *
 * @param support Platform support containing the Android [android.content.Context].
 * @param registry Worker registry containing registered worker factories.
 * @return The initialized [Chronos] engine instance.
 */
public actual fun Chronos.Companion.initialize(
    support: PlatformSupport,
    registry: WorkerRegistry
): Chronos {
    val impl = buildAndroidChronos(support = support, registry = registry)
    ChronosRegistry.set(impl)
    return impl
}

/**
 * Assembles and wires together the Android runtime dependencies for [ChronosEngine].
 *
 * Wires the database, coroutine scopes, [ChronosWakeScheduler], [TaskCoordinator],
 * [Daemon], [ChronosTaskEnqueuer], [ChronosTaskCanceller], and [RoomTaskStore].
 *
 * @param support Platform support containing the application context.
 * @param registry Registered worker mapping.
 * @return A fully assembled [ChronosEngine].
 */
internal fun buildAndroidChronos(
    support: PlatformSupport,
    registry: WorkerRegistry
): ChronosEngine {
    val appContext = support.context.applicationContext
    val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = createRoomDatabase(support = support)
    val wakeScheduler = ChronosWakeScheduler(context = appContext, db = db)

    val taskExecutor = InProcessTaskExecutor(db = db, registry = registry)
    val coordinator = TaskCoordinator(
        db = db,
        taskExecutor = taskExecutor,
        constraintProvider = AndroidConstraintVerifier(context = appContext),
        wakeScheduler = wakeScheduler
    )
    val daemon = Daemon(coroutineScope = coroutineScope, coordinator = coordinator, db = db)
    val enqueuer = ChronosTaskEnqueuer(
        db = db,
        coroutineScope = coroutineScope,
        coordinator = coordinator,
        wakeScheduler = wakeScheduler
    )
    val canceller = ChronosTaskCanceller(
        db = db,
        coroutineScope = coroutineScope,
        wakeScheduler = wakeScheduler
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
        onClose = { db.close() },
        wakeScheduler = wakeScheduler
    )
}
