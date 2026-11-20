package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.constraints.IosConstraintVerifier
import io.github.sahilmakkar.chronos.engine.ChronosEngine
import io.github.sahilmakkar.chronos.engine.Daemon
import io.github.sahilmakkar.chronos.engine.TaskCoordinator
import io.github.sahilmakkar.chronos.executor.InProcessTaskExecutor
import io.github.sahilmakkar.chronos.scheduler.ChronosTaskCanceller
import io.github.sahilmakkar.chronos.scheduler.ChronosTaskEnqueuer
import io.github.sahilmakkar.chronos.scheduler.IosWakeScheduler
import io.github.sahilmakkar.chronos.storage.RoomTaskStore
import io.github.sahilmakkar.chronos.storage.createRoomDatabase
import io.github.sahilmakkar.chronos.worker.WorkerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Apple platform entry point implementing [Chronos.Companion.initialize].
 *
 * Initializes the Room SQLite database in the application documents sandbox (`<Documents>/chronos_work.db`),
 * configures Apple `BGTaskScheduler` background refresh handling via [IosWakeScheduler],
 * initializes [IosConstraintVerifier], and publishes the singleton instance accessible via [Chronos.Companion.instance].
 *
 * @param support Apple platform support provider.
 * @param registry Worker registry containing registered worker factories.
 * @return The initialized [Chronos] engine instance.
 */
public actual fun Chronos.Companion.initialize(
    support: PlatformSupport,
    registry: WorkerRegistry,
): Chronos {
    val impl = buildIosChronos(support = support, registry = registry)
    ChronosRegistry.set(instance = impl)
    return impl
}

/**
 * Assembles and wires together the Apple/iOS runtime dependencies for [ChronosEngine].
 *
 * Wires the Room database, background coroutine scopes, [IosWakeScheduler],
 * [TaskCoordinator], [Daemon], [ChronosTaskEnqueuer], [ChronosTaskCanceller], and [RoomTaskStore].
 *
 * @param support Apple platform support provider.
 * @param registry Registered worker mapping.
 * @return A fully assembled [ChronosEngine].
 */
internal fun buildIosChronos(
    support: PlatformSupport,
    registry: WorkerRegistry,
): ChronosEngine {
    val coroutineScope = CoroutineScope(context = SupervisorJob() + Dispatchers.Default)
    val db = createRoomDatabase(support = support)
    val networkChecker = IosNetworkChecker()

    val taskExecutor = InProcessTaskExecutor(db = db, registry = registry)
    val constraintVerifier = IosConstraintVerifier(networkChecker = networkChecker)

    // Built first so the coordinator can reference it; its callbacks are resolved lazily at wake time.
    lateinit var coordinator: TaskCoordinator
    val wakeScheduler =
        IosWakeScheduler(
            db = db,
            coroutineScope = coroutineScope,
            onWake = { coordinator.runPendingTasks() },
            onExpired = { coordinator.sweepOrphans() },
        )
    coordinator =
        TaskCoordinator(
            db = db,
            taskExecutor = taskExecutor,
            constraintProvider = constraintVerifier,
            wakeScheduler = wakeScheduler,
        )

    val daemon =
        Daemon(
            coroutineScope = coroutineScope,
            coordinator = coordinator,
            db = db,
            scheduler = wakeScheduler,
        )
    val enqueuer =
        ChronosTaskEnqueuer(
            db = db,
            coroutineScope = coroutineScope,
            coordinator = coordinator,
            wakeScheduler = wakeScheduler,
        )
    val canceller = ChronosTaskCanceller(db = db, coroutineScope = coroutineScope, wakeScheduler = wakeScheduler)
    val taskStore = RoomTaskStore(dao = db.taskDao())

    return ChronosEngine(
        registry = registry,
        coroutineScope = coroutineScope,
        coordinator = coordinator,
        daemon = daemon,
        enqueuer = enqueuer,
        canceller = canceller,
        taskStore = taskStore,
        onClose = { db.close() },
        wakeScheduler = wakeScheduler,
    )
}
