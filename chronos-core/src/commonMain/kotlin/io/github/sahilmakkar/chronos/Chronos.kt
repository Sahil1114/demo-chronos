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
 */
public interface Chronos : AutoCloseable {

    /**
     * Enqueues a one-time or periodic task request for execution.
     */
    public fun enqueue(request: TaskRequest)

    /**
     * Enqueues a unique one-time task using a designated policy for duplicate resolution.
     */
    public fun enqueueUniqueTask(name: String, policy: UniqueTaskPolicy, request: OneTimeTaskRequest)

    /**
     * Enqueues a unique periodic task using a designated policy for duplicate resolution.
     */
    public fun enqueueUniquePeriodicTask(name: String, policy: UniqueTaskPolicy, request: PeriodicTaskRequest)

    /**
     * Enqueues a chain of sequential tasks.
     */
    public fun enqueue(chain: TaskChain)

    /**
     * Cancels a task by its unique identifier.
     */
    public fun cancelTaskById(id: String)

    /**
     * Cancels all tasks associated with the specified tag.
     */
    public fun cancelTaskByTag(tag: String)

    /**
     * Cancels all currently tracked tasks.
     */
    public fun cancelAllTasks()

    /**
     * Observes real-time execution status updates for the task with the given ID.
     */
    public fun observeTask(id: String): Flow<TaskInfo?>

    /**
     * Observes real-time execution status updates for tasks tagged with the given tag.
     */
    public fun observeTasksByTag(tag: String): Flow<List<TaskInfo>>

    /**
     * Manually triggers an immediate execution pass for all pending eligible tasks.
     */
    public suspend fun runPendingTasksNow()

    /**
     * Registry of worker factories used by this Chronos instance.
     */
    public val registry: WorkerRegistry

    public companion object
}

/**
 * Initializes the process-wide Chronos singleton instance for the platform.
 */
public expect fun Chronos.Companion.initialize(support: PlatformSupport, registry: WorkerRegistry): Chronos

/**
 * Returns the currently initialized process-wide Chronos singleton instance.
 * Throws an [IllegalStateException] if Chronos has not been initialized.
 */
public val Chronos.Companion.instance: Chronos
    get() = ChronosRegistry.instance

/**
 * Shuts down and closes the current Chronos instance.
 */
public fun Chronos.Companion.shutdown(): Unit = ChronosRegistry.clear()

/**
 * Sets a Chronos instance for testing purposes.
 */
public fun Chronos.Companion.setForTesting(instance: Chronos): Unit = ChronosRegistry.set(instance)

/**
 * Begins a task execution chain starting with the specified task request.
 */
public fun Chronos.beginWith(request: TaskRequest): TaskChain = TaskChain.start(request)
