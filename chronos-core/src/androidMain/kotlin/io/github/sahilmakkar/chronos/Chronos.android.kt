package io.github.sahilmakkar.chronos

import android.content.Context
import androidx.work.WorkManager
import io.github.sahilmakkar.chronos.executor.AndroidTaskExecutor
import io.github.sahilmakkar.chronos.executor.WorkConstraintVerifier
import io.github.sahilmakkar.chronos.network.NetworkChecker
import io.github.sahilmakkar.chronos.roomDb.WorkDatabase
import io.github.sahilmakkar.chronos.roomDb.createRoomDatabase
import io.github.sahilmakkar.chronos.roomDb.executor.WorkChainCoordinator
import io.github.sahilmakkar.chronos.scheduler.PlatformScheduler
import io.github.sahilmakkar.chronos.scheduler.WorkObserver
import io.github.sahilmakkar.chronos.scheduler.canceller.ChronosWorkCanceller
import io.github.sahilmakkar.chronos.scheduler.canceller.WorkCanceller
import io.github.sahilmakkar.chronos.scheduler.workEnqueuer.ChronosWorkEnqueuer
import io.github.sahilmakkar.chronos.scheduler.workEnqueuer.WorkEnqueuer
import io.github.sahilmakkar.chronos.task.TaskChain
import io.github.sahilmakkar.chronos.task.model.TaskInfo
import io.github.sahilmakkar.chronos.task.policy.UniqueTaskPolicy
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import io.github.sahilmakkar.chronos.task.request.PeriodicTaskRequest
import io.github.sahilmakkar.chronos.task.request.TaskRequest
import io.github.sahilmakkar.chronos.worker.ChronosCore
import io.github.sahilmakkar.chronos.worker.WorkerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow

actual class Chronos actual constructor(
    actual val registry: WorkerRegistry,
) : WorkEnqueuer,
    WorkCanceller,
    WorkObserver,
    PlatformScheduler,
    AutoCloseable {

    // Instance-scoped, not a top-level global. Only ever set by the
    // (Context, WorkerRegistry) constructor below.
    private var androidContext: Context? = null

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * The registry-only primary constructor exists to satisfy the
     * `expect class Chronos` signature, but Chronos is not functional on
     * Android without a Context. All real startup work happens here --
     * after androidContext is assigned -- rather than in an init block on
     * the primary constructor, to avoid racing daemon.start() against this
     * assignment (see prior discussion on constructor ordering).
     */
    constructor(
        context: Context,
        registry: WorkerRegistry,
    ) : this(registry = registry) {
        androidContext = context.applicationContext
        Chronos.initialize(this)
        daemon.start()
    }

    private val workManager: WorkManager by lazy {
        WorkManager.getInstance(
            androidContext ?: error("Android Context not initialized. Use the Context constructor.")
        )
    }

    actual val networkChecker: NetworkChecker by lazy {
        AndroidNetworkChecker(
            androidContext ?: error("Android Context not initialized. Use the Context constructor.")
        )
    }

    private val db: WorkDatabase = createRoomDatabase()

    private val taskExecutor by lazy {
        AndroidTaskExecutor(workManager = workManager, db = db)
    }

    private val coordinator by lazy {
        WorkChainCoordinator(
            db = db,
            taskExecutor = taskExecutor,
            constraintProvider = WorkConstraintVerifier()
        )
    }

    private val daemon by lazy {
        Daemon(coroutineScope = coroutineScope, coordinator = coordinator)
    }

    private val enqueuer by lazy {
        ChronosWorkEnqueuer(db = db, coroutineScope = coroutineScope, coordinator = coordinator)
    }

    private val canceller by lazy {
        ChronosWorkCanceller(
            workManager = workManager,
            db = db,
            coroutineScope = coroutineScope
        )
    }

    // All shared, non-platform-specific behaviour lives in ChronosCore.
    // This class's only job past this point is: build Android-flavoured
    // dependencies above, then hand them off.
    private val core by lazy {
        ChronosCore(
            db = db,
            coroutineScope = coroutineScope,
            enqueuer = enqueuer,
            canceller = canceller,
            coordinator = coordinator,
            daemon = daemon
        )
    }

    actual companion object {}

    // --- WorkEnqueuer ---

    actual override fun enqueue(request: TaskRequest) =
        core.enqueue(request)

    actual override fun enqueueUniqueWork(
        name: String,
        policy: UniqueTaskPolicy,
        request: OneTimeTaskRequest
    ) = core.enqueueUniqueWork(name, policy, request)

    actual override fun enqueueUniquePeriodicWork(
        name: String,
        policy: UniqueTaskPolicy,
        request: PeriodicTaskRequest
    ) = core.enqueueUniquePeriodicWork(name, policy, request)

    actual override fun enqueue(chain: TaskChain) =
        core.enqueue(chain)

    // --- WorkCanceller ---

    actual override fun cancelWorkById(id: String) =
        core.cancelWorkById(id)

    actual override fun cancelWorkByTag(tag: String) =
        core.cancelWorkByTag(tag)

    actual override fun cancelAllWork() =
        core.cancelAllWork()

    // --- WorkObserver ---
    // Reads from Room -- Room is source of truth, not WorkManager

    actual override fun getWorkInfoFlow(id: String): Flow<TaskInfo?> =
        core.getWorkInfoFlow(id)

    actual override fun getWorkInfosByTagFlow(tag: String): Flow<List<TaskInfo>> =
        core.getWorkInfosByTagFlow(tag)

    // --- PlatformScheduler ---

    actual override suspend fun runPendingTasksNow() =
        core.runPendingTasksNow()

    // --- AutoCloseable ---

    actual override fun close() = core.close()
    // WorkManager manages its own lifecycle -- no close needed
}