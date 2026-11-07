package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.constraints.JvmConstraintVerifier
import io.github.sahilmakkar.chronos.engine.ChronosEngine
import io.github.sahilmakkar.chronos.engine.Daemon
import io.github.sahilmakkar.chronos.engine.TaskCoordinator
import io.github.sahilmakkar.chronos.executor.InProcessTaskExecutor
import io.github.sahilmakkar.chronos.scheduler.ChronosTaskCanceller
import io.github.sahilmakkar.chronos.scheduler.ChronosTaskEnqueuer
import io.github.sahilmakkar.chronos.storage.RoomTaskStore
import io.github.sahilmakkar.chronos.storage.createRoomDatabase
import io.github.sahilmakkar.chronos.worker.WorkerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * JVM platform entry point implementing [Chronos.Companion.initialize].
 *
 * Initializes the Room SQLite database on disk (`~/.chronos/chronos_work.db`),
 * configures JVM constraint verification, launches the background [Daemon] polling loop,
 * and publishes the singleton instance accessible via [Chronos.Companion.instance].
 *
 * @param support JVM platform support provider.
 * @param registry Worker registry containing registered worker factories.
 * @return The initialized [Chronos] engine instance.
 */
public actual fun Chronos.Companion.initialize(
    support: PlatformSupport,
    registry: WorkerRegistry
): Chronos {
    val impl = buildJvmChronos(support = support, registry = registry)
    ChronosRegistry.set(impl)
    return impl
}

/**
 * Assembles and wires together the JVM runtime dependencies for [ChronosEngine].
 *
 * Wires the Room database, background coroutine scopes, [TaskCoordinator],
 * [Daemon], [ChronosTaskEnqueuer], [ChronosTaskCanceller], and [RoomTaskStore].
 *
 * @param support Platform support provider for JVM environments.
 * @param registry Registered worker mapping.
 * @return A fully assembled [ChronosEngine].
 */
internal fun buildJvmChronos(support: PlatformSupport, registry: WorkerRegistry): ChronosEngine {
    val db = createRoomDatabase(support)
    val coroutineScope = CoroutineScope(context = SupervisorJob() + Dispatchers.Default)
    val networkChecker = JvmNetworkChecker()

    val taskExecutor = InProcessTaskExecutor(db = db, registry = registry)
    val constraintVerifier = JvmConstraintVerifier(networkChecker = networkChecker)
    val coordinator = TaskCoordinator(
        db = db,
        constraintProvider = constraintVerifier,
        taskExecutor = taskExecutor
    )
    val daemon = Daemon(coroutineScope = coroutineScope, coordinator = coordinator, db = db)
    val enqueuer = ChronosTaskEnqueuer(db = db, coroutineScope = coroutineScope, coordinator = coordinator)
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
        onClose = { db.close() }
    )
}
