package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.task.TaskChain
import io.github.sahilmakkar.chronos.task.model.TaskInfo
import io.github.sahilmakkar.chronos.task.policy.UniqueTaskPolicy
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import io.github.sahilmakkar.chronos.task.request.PeriodicTaskRequest
import io.github.sahilmakkar.chronos.task.request.TaskRequest
import io.github.sahilmakkar.chronos.worker.WorkerRegistry
import kotlinx.coroutines.flow.Flow

/**
 * Main entry point and coordinator for the Chronos background task scheduling framework.
 *
 * Chronos provides persistent, constraint-aware, battery-efficient background task execution
 * across Kotlin Multiplatform targets (Android, iOS, and JVM). Tasks enqueued into Chronos are
 * durably persisted in an embedded Room SQLite database, surviving application termination and device restarts.
 *
 * ### Core Capabilities:
 * - **Persistence:** Tasks survive app restarts and process death.
 * - **Constraints:** Gate task execution on network connectivity, device charging state, battery level,
 *   device idle state, and specific time-of-day execution windows.
 * - **Execution Chains:** Run dependent tasks sequentially, with configurable failure propagation policies
 *   ([io.github.sahilmakkar.chronos.task.policy.DependentTaskPolicy.STOP_ON_FAILURE] vs
 *   [io.github.sahilmakkar.chronos.task.policy.DependentTaskPolicy.CONTINUE_ON_FAILURE]).
 * - **Unique Tasks:** Prevent duplicate concurrent or pending executions via
 *   [io.github.sahilmakkar.chronos.task.policy.UniqueTaskPolicy] (`KEEP`, `REPLACE`, or `APPEND`).
 * - **Real-Time Observation:** Observe reactive [Flow] streams of task lifecycle states, progress payloads,
 *   and terminal outputs.
 * - **Platform-Native Background Wake-Up:** Integrates with Android WorkManager via a wake-up worker
 *   ([ChronosWakeWorker]) and iOS `BGTaskScheduler` to wake the runtime when scheduled tasks are due.
 *
 * ### Example Initialization:
 * ```kotlin
 * val registry = WorkerRegistry().apply {
 *     register("DownloadWorker") { input -> DownloadWorker(input) }
 * }
 * Chronos.initialize(PlatformSupport(context), registry)
 * ```
 *
 * ### Example Usage:
 * ```kotlin
 * val request = OneTimeTaskRequest.Builder(SyncWorker::class)
 *     .setInputData(taskDataOf("userId" to "123"))
 *     .setConstraints(EnvironmentConstraints(requiredNetworkType = NetworkType.CONNECTED))
 *     .build()
 *
 * Chronos.instance.enqueue(request)
 * Chronos.instance.observeTask(request.id).collect { info ->
 *     println("Task state: ${info?.state}")
 * }
 * ```
 *
 * @see TaskRequest
 * @see OneTimeTaskRequest
 * @see PeriodicTaskRequest
 * @see TaskChain
 * @see io.github.sahilmakkar.chronos.worker.TaskWorker
 */
public interface Chronos : AutoCloseable {

    /**
     * Enqueues a one-time or periodic task request for execution.
     *
     * The task is durably saved in the persistent database. If the task has no unsatisfied initial delays,
     * dependencies, or unmet environment constraints, it becomes immediately eligible for execution and
     * a coordinator dispatch pass is scheduled.
     *
     * On Android, if the task has a future scheduled execution time, the wake scheduler automatically
     * registers or updates a WorkManager one-off wake request.
     *
     * @param request The task request specification to enqueue, containing worker parameters, delays,
     * retry policies, and constraints.
     */
    public fun enqueue(request: TaskRequest)

    /**
     * Enqueues a unique one-time task identified by a distinct string name, applying the given
     * [UniqueTaskPolicy] to resolve conflicts if a task with the same name already exists in an active state.
     *
     * Conflict resolution policies:
     * - [UniqueTaskPolicy.KEEP]: If an active task with [name] already exists, the new request is ignored.
     * - [UniqueTaskPolicy.REPLACE]: Cancels and clears the existing active task, replacing it with [request].
     * - [UniqueTaskPolicy.APPEND]: Attaches the new task to execute sequentially after the existing active task's
     *   completion, extending the execution chain.
     *
     * @param name Unique task identifier name used to detect conflicts across enqueues.
     * @param policy Policy defining how to handle existing active tasks with the same unique name.
     * @param request The one-time task request to enqueue.
     * @see UniqueTaskPolicy
     */
    public fun enqueueUniqueTask(name: String, policy: UniqueTaskPolicy, request: OneTimeTaskRequest)

    /**
     * Enqueues a unique periodic task identified by a distinct string name, applying the given
     * [UniqueTaskPolicy] to resolve conflicts if a periodic task with the same name already exists.
     *
     * Note: [UniqueTaskPolicy.APPEND] is not supported for periodic tasks and will throw an
     * [IllegalArgumentException] if supplied.
     *
     * @param name Unique periodic task identifier name used to detect conflicts across enqueues.
     * @param policy Conflict resolution policy; must be either [UniqueTaskPolicy.KEEP] or [UniqueTaskPolicy.REPLACE].
     * @param request The periodic task request configuring the recurrence interval and execution criteria.
     * @throws IllegalArgumentException If [policy] is set to [UniqueTaskPolicy.APPEND].
     * @see UniqueTaskPolicy
     */
    public fun enqueueUniquePeriodicTask(name: String, policy: UniqueTaskPolicy, request: PeriodicTaskRequest)

    /**
     * Enqueues a sequential chain of dependent tasks.
     *
     * Tasks in the chain are inserted in a single database transaction. Downstream tasks remain
     * in the [io.github.sahilmakkar.chronos.task.TaskState.ENQUEUED] state until their immediate parent task
     * reaches [io.github.sahilmakkar.chronos.task.TaskState.SUCCEEDED].
     *
     * If a parent task fails or exhausts its retry attempts, downstream tasks are handled according to the
     * [io.github.sahilmakkar.chronos.task.policy.DependentTaskPolicy] specified when constructing the chain step.
     *
     * @param chain The task chain definition specifying the ordered list of task requests and failure policies.
     * @see TaskChain
     * @see Chronos.beginWith
     */
    public fun enqueue(chain: TaskChain)

    /**
     * Cancels an individual task by its unique canonical ID.
     *
     * If the task is currently active ([io.github.sahilmakkar.chronos.task.TaskState.ENQUEUED],
     * [io.github.sahilmakkar.chronos.task.TaskState.RUNNING], or [io.github.sahilmakkar.chronos.task.TaskState.RETRYING]),
     * its state is transitioned to [io.github.sahilmakkar.chronos.task.TaskState.CANCELLED].
     * If the task has downstream dependent tasks in an execution chain, they are recursively cancelled as well.
     *
     * Tasks already in terminal states ([io.github.sahilmakkar.chronos.task.TaskState.SUCCEEDED],
     * [io.github.sahilmakkar.chronos.task.TaskState.FAILED], or already `CANCELLED`) remain unchanged.
     *
     * @param id Canonical 36-character UUID string identifying the target task.
     */
    public fun cancelTaskById(id: String)

    /**
     * Cancels all active tasks that have been tagged with the specified string tag.
     *
     * Any task tagged with [tag] currently in an active state is transitioned to
     * [io.github.sahilmakkar.chronos.task.TaskState.CANCELLED], and its downstream chain dependents
     * are recursively cancelled.
     *
     * @param tag Categorization tag associated with the tasks to cancel.
     */
    public fun cancelTaskByTag(tag: String)

    /**
     * Cancels all active tasks currently tracked by the Chronos database.
     *
     * Every task currently in [io.github.sahilmakkar.chronos.task.TaskState.ENQUEUED],
     * [io.github.sahilmakkar.chronos.task.TaskState.RUNNING], or
     * [io.github.sahilmakkar.chronos.task.TaskState.RETRYING] is transitioned to
     * [io.github.sahilmakkar.chronos.task.TaskState.CANCELLED]. Any pending platform wake alarms are cancelled.
     */
    public fun cancelAllTasks()

    /**
     * Observes real-time execution status and diagnostic updates for the task with the given ID.
     *
     * The returned [Flow] emits a new [TaskInfo] snapshot whenever the task's state, progress payload,
     * or output payload changes in the database. If the task does not exist or has been deleted,
     * the flow emits `null`.
     *
     * @param id Canonical 36-character UUID string of the task to observe.
     * @return Cold [Flow] emitting updated [TaskInfo] instances or null if not found.
     */
    public fun observeTask(id: String): Flow<TaskInfo?>

    /**
     * Observes real-time execution status updates for all tasks matching the specified tag.
     *
     * The returned [Flow] emits an updated list of [TaskInfo] instances whenever any task carrying
     * the given tag is inserted or updated.
     *
     * @param tag Categorization tag identifier to observe.
     * @return Cold [Flow] emitting lists of matching [TaskInfo] snapshots.
     */
    public fun observeTasksByTag(tag: String): Flow<List<TaskInfo>>

    /**
     * Manually triggers an immediate execution pass for all pending eligible tasks.
     *
     * Queries the database for tasks in [io.github.sahilmakkar.chronos.task.TaskState.ENQUEUED] or
     * [io.github.sahilmakkar.chronos.task.TaskState.RETRYING] states whose scheduled execution time has passed,
     * whose chain dependencies are satisfied, and whose environment constraints are met.
     *
     * Eligible tasks are claimed atomically and dispatched concurrently up to the configured concurrency cap.
     * This function suspends until the execution pass and all triggered chain passes complete.
     */
    public suspend fun runPendingTasksNow()

    /**
     * The registry holding [io.github.sahilmakkar.chronos.worker.TaskWorker] factory bindings
     * used by this Chronos engine to instantiate workers at runtime.
     */
    public val registry: WorkerRegistry

    public companion object
}

/**
 * Initializes the process-wide Chronos singleton instance for the current platform runtime.
 *
 * This function should typically be called once during application startup (e.g. inside `Application.onCreate`
 * on Android, or at application launch on iOS/Desktop).
 *
 * Calling this method registers the created instance globally, making it accessible via [Chronos.Companion.instance].
 * If a Chronos instance is already initialized, calling this will replace it and shut down the previous instance.
 *
 * @param support Platform-specific environment bindings (such as `Context` on Android).
 * @param registry Worker registry containing registered worker factories.
 * @return The newly initialized [Chronos] singleton instance.
 */
public expect fun Chronos.Companion.initialize(support: PlatformSupport, registry: WorkerRegistry): Chronos

/**
 * Returns the currently initialized process-wide Chronos singleton instance.
 *
 * @throws IllegalStateException If [Chronos.Companion.initialize] has not yet been called in this process.
 * @see Chronos.Companion.initialize
 */
public val Chronos.Companion.instance: Chronos
    get() = ChronosRegistry.instance

/**
 * Shuts down and closes the current Chronos singleton instance.
 *
 * Cancels background execution scopes, terminates the internal daemon loops, and releases database resources.
 * Clears the singleton reference so subsequent calls to [Chronos.Companion.instance] will fail until re-initialized.
 */
public fun Chronos.Companion.shutdown(): Unit = ChronosRegistry.clear()

/**
 * Replaces the current process-wide Chronos singleton instance with a custom or fake instance for testing.
 *
 * @param instance The test-doubled or mock [Chronos] implementation to set as the active singleton.
 */
public fun Chronos.Companion.setForTesting(instance: Chronos): Unit = ChronosRegistry.set(instance)

/**
 * Begins a fluent task execution chain starting with the specified task request.
 *
 * Additional sequential task steps can be chained onto the returned [TaskChain] using [TaskChain.then].
 *
 * ### Example:
 * ```kotlin
 * chronos.beginWith(downloadTask)
 *     .then(processTask)
 *     .then(uploadTask)
 *     .also { chain -> chronos.enqueue(chain) }
 * ```
 *
 * @param request The initial task request to run first in the sequence.
 * @return A new single-step [TaskChain] starting with [request].
 * @see TaskChain
 */
public fun Chronos.beginWith(request: TaskRequest): TaskChain = TaskChain.start(request)
