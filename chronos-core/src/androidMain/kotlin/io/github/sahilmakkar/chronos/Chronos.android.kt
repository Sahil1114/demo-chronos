package io.github.sahilmakkar.chronos

import android.content.Context
import androidx.work.WorkManager
import io.github.sahilmakkar.chronos.executor.AndroidTaskExecutor
import io.github.sahilmakkar.chronos.executor.WorkConstraintVerifier
import io.github.sahilmakkar.chronos.kmpWorker.WorkerRegistry
import io.github.sahilmakkar.chronos.network.NetworkChecker
import io.github.sahilmakkar.chronos.roomDb.WorkDatabase
import io.github.sahilmakkar.chronos.roomDb.createRoomDatabase
import io.github.sahilmakkar.chronos.roomDb.executor.WorkChainCoordinator
import io.github.sahilmakkar.chronos.roomDb.toWorkInfo
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

actual class Chronos actual constructor(
    actual val registry: WorkerRegistry,
) : WorkEnqueuer,
    WorkCanceller,
    WorkObserver,
    PlatformScheduler,
    AutoCloseable {

    constructor(
        context: Context,
        registry: WorkerRegistry,
    ) : this(registry = registry) {
        androidContext = context.applicationContext
        Chronos.initialize(this)
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

    val db: WorkDatabase = createRoomDatabase()

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

    actual companion object {}

    init {
        Chronos.initialize(this)
        coroutineScope.launch {
            coordinator.sweepOrphans()
            coordinator.sweepChainRecovery()
            coordinator.runPendingTasks()
        }
        startDaemonLoop()
    }

    // --- WorkEnqueuer ---

    actual override fun enqueue(request: TaskRequest) =
        enqueuer.enqueue(request)

    actual override fun enqueueUniqueWork(
        name: String,
        policy: UniqueTaskPolicy,
        request: OneTimeTaskRequest
    ) = enqueuer.enqueueUniqueWork(name, policy, request)

    actual override fun enqueueUniquePeriodicWork(
        name: String,
        policy: UniqueTaskPolicy,
        request: PeriodicTaskRequest
    ) = enqueuer.enqueueUniquePeriodicWork(name, policy, request)

    actual override fun enqueue(chain: TaskChain) =
        enqueuer.enqueue(chain)

    // --- WorkCanceller ---

    actual override fun cancelWorkById(id: String) =
        canceller.cancelWorkById(id)

    actual override fun cancelWorkByTag(tag: String) =
        canceller.cancelWorkByTag(tag)

    actual override fun cancelAllWork() =
        canceller.cancelAllWork()

    // --- WorkObserver ---
    // Reads from Room -- Room is source of truth, not WorkManager

    actual override fun getWorkInfoFlow(id: String): Flow<TaskInfo?> =
        db.workDao().observeWorkRequest(id).map { it?.toWorkInfo() }

    actual override fun getWorkInfosByTagFlow(tag: String): Flow<List<TaskInfo>> =
        db.workDao().observeWorkRequestsByTag(tag).map { list ->
            list.map { it.toWorkInfo() }
        }

    // --- PlatformScheduler ---

    actual override suspend fun runPendingTasksNow() =
        coordinator.runPendingTasks()

    // --- AutoCloseable ---

    actual override fun close() {
        coroutineScope.cancel()
        db.close()
        // WorkManager manages its own lifecycle -- no close needed
    }

    // --- Daemon ---

    private fun startDaemonLoop() {
        coroutineScope.launch {
            while (isActive) {
                try {
                    coordinator.runPendingTasks()
                } catch (e: Exception) {
                }
                delay(1000)
            }
        }
    }
}

var androidContext: Context? = null