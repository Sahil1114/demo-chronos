package io.github.sahilmakkar.chronos

import android.content.Context
import androidx.work.WorkManager
import io.github.sahilmakkar.chronos.task.model.TaskInfo
import io.github.sahilmakkar.chronos.kmpWorker.KmpWorkerRegistry
import io.github.sahilmakkar.chronos.task.policy.UniqueTaskPolicy
import io.github.sahilmakkar.chronos.task.TaskChain
import io.github.sahilmakkar.chronos.mapper.WorkInfoMapper
import io.github.sahilmakkar.chronos.network.NetworkChecker
import io.github.sahilmakkar.chronos.scheduler.PlatformScheduler
import io.github.sahilmakkar.chronos.scheduler.WorkObserver
import io.github.sahilmakkar.chronos.scheduler.canceller.ChronosWorkCanceller
import io.github.sahilmakkar.chronos.scheduler.canceller.WorkCanceller
import io.github.sahilmakkar.chronos.scheduler.workEnqueuer.ChronosWorkEnqueuer
import io.github.sahilmakkar.chronos.scheduler.workEnqueuer.WorkEnqueuer
import io.github.sahilmakkar.chronos.utils.KmpClock
import io.github.sahilmakkar.chronos.utils.SystemClock
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import io.github.sahilmakkar.chronos.task.request.PeriodicTaskRequest
import io.github.sahilmakkar.chronos.task.request.TaskRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

actual class Chronos actual constructor(
    actual val registry: KmpWorkerRegistry,
    actual val clock: KmpClock
) : WorkEnqueuer,
    WorkCanceller,
    WorkObserver,
    PlatformScheduler,
    AutoCloseable {

    constructor(
        context: Context,
        registry: KmpWorkerRegistry,
        clock: KmpClock = SystemClock
    ) : this(registry, clock) {
        androidContext = context.applicationContext
        Chronos.initialize(this)
    }

    private val workManager: WorkManager by lazy {
        val ctx = androidContext ?: throw IllegalStateException(
            "Android Context not initialized. Use the Context constructor."
        )
        WorkManager.getInstance(ctx)
    }

    actual val networkChecker: NetworkChecker by lazy {
        val ctx = androidContext ?: throw IllegalStateException(
            "Android Context not initialized. Use the Context constructor."
        )
        AndroidNetworkChecker(ctx)
    }

    private val enqueuer: ChronosWorkEnqueuer by lazy {
       ChronosWorkEnqueuer(workManager)
    }

    private val canceller: ChronosWorkCanceller by lazy {
       ChronosWorkCanceller(workManager)
    }

    actual companion object {}

    // --- WorkEnqueuer — delegated to ChronosWorkEnqueuer ---

    actual override fun enqueue(request: TaskRequest) = enqueuer.enqueue(request)

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

    actual override fun enqueue(chain: TaskChain) = enqueuer.enqueue(chain)

    // --- WorkCanceller — delegated to ChronosWorkCanceller ---

    actual override fun cancelWorkById(id: String) = canceller.cancelWorkById(id)
    actual override fun cancelWorkByTag(tag: String) = canceller.cancelWorkByTag(tag)
    actual override fun cancelAllWork() = canceller.cancelAllWork()

    // --- WorkObserver ---

    actual override fun getWorkInfoFlow(id: String): Flow<TaskInfo?> =
        workManager.getWorkInfoByIdFlow(UUID.fromString(id)).map {
            with(WorkInfoMapper) { it?.toWorkInfo() }
        }

    actual override fun getWorkInfosByTagFlow(tag: String): Flow<List<TaskInfo>> =
        workManager.getWorkInfosByTagFlow(tag).map { list ->
            with(WorkInfoMapper) { list.map { it.toWorkInfo() } }
        }

    // --- PlatformScheduler ---

    actual override suspend fun runPendingTasksNow() {}

    actual override fun close() {}
}
var androidContext: Context? = null
