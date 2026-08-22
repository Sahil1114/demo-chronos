package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.task.model.TaskInfo
import io.github.sahilmakkar.chronos.kmpWorker.KmpWorkerRegistry
import io.github.sahilmakkar.chronos.task.policy.UniqueTaskPolicy
import io.github.sahilmakkar.chronos.task.TaskChain
import io.github.sahilmakkar.chronos.network.NetworkChecker
import io.github.sahilmakkar.chronos.scheduler.PlatformScheduler
import io.github.sahilmakkar.chronos.scheduler.WorkObserver
import io.github.sahilmakkar.chronos.scheduler.canceller.WorkCanceller
import io.github.sahilmakkar.chronos.scheduler.workEnqueuer.WorkEnqueuer
import io.github.sahilmakkar.chronos.utils.KmpClock
import io.github.sahilmakkar.chronos.utils.SystemClock
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import io.github.sahilmakkar.chronos.task.request.PeriodicTaskRequest
import io.github.sahilmakkar.chronos.task.request.TaskRequest
import kotlinx.coroutines.flow.Flow

/**
 * Main entry point for scheduling background work in Kotlin Multiplatform.
 *
 * `Chronos` is a thin coordinator. Each responsibility is delegated to a
 * dedicated expect/actual class whose implementation varies per platform:
 * - [io.github.sahilmakkar.chronos.scheduler.workEnqueuer.ChronosWorkEnqueuer] — scheduling new work
 * - [io.github.sahilmakkar.chronos.scheduler.canceller.ChronosWorkCanceller] — cancelling work
 *
 * Prefer injecting a narrower interface at call sites:
 * - [WorkEnqueuer]      — scheduling
 * - [WorkCanceller]     — cancellation
 * - [WorkObserver]      — state observation
 * - [PlatformScheduler] — platform capabilities and manual task execution
 * - [AutoCloseable]     — lifecycle management
 */
expect class Chronos(
    registry: KmpWorkerRegistry,
    clock: KmpClock = SystemClock
) : WorkEnqueuer,
    WorkCanceller,
    WorkObserver,
    PlatformScheduler,
    AutoCloseable {

    val registry: KmpWorkerRegistry
    val clock: KmpClock
    val networkChecker: NetworkChecker

    // --- WorkEnqueuer — delegated to ChronosWorkEnqueuer ---

    override fun enqueue(request: TaskRequest)
    override fun enqueueUniqueWork(
        name: String,
        policy: UniqueTaskPolicy,
        request: OneTimeTaskRequest
    )

    override fun enqueueUniquePeriodicWork(
        name: String,
        policy: UniqueTaskPolicy,
        request: PeriodicTaskRequest
    )

    override fun enqueue(chain: TaskChain)

    // --- WorkCanceller — delegated to ChronosWorkCanceller ---

    override fun cancelWorkById(id: String)
    override fun cancelWorkByTag(tag: String)
    override fun cancelAllWork()

    // --- WorkObserver ---

    override fun getWorkInfoFlow(id: String): Flow<TaskInfo?>
    override fun getWorkInfosByTagFlow(tag: String): Flow<List<TaskInfo>>

    // --- PlatformScheduler ---
    override suspend fun runPendingTasksNow()

    // --- AutoCloseable ---

    override fun close()

    companion object

}

// region Singleton

private var _instance: Chronos? = null

/**
 * The global [Chronos] singleton.
 *
 * @throws IllegalStateException if accessed before [initialize] is called.
 */
val Chronos.Companion.instance: Chronos
    get() = _instance ?: throw IllegalStateException(
        "Chronos has not been initialized. Call Chronos.initialize(...) first."
    )

/** Installs [instance] as the global [Chronos] singleton. */
fun Chronos.Companion.initialize(instance: Chronos) {
    _instance = instance
}

/**
 * Starts building a dependent task chain beginning with [request].
 *
 * Usage:
 * ```
 * chronos.beginWith(uploadRequest)
 *     .then(notifyRequest)
 *     .enqueue()
 * ```
 */
fun Chronos.beginWith(request: TaskRequest): TaskChain =
    TaskChain(chronos = this, requests = listOf(request), failurePolicies = emptyList())