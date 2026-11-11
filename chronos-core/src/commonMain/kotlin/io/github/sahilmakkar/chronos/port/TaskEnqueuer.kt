package io.github.sahilmakkar.chronos.port

import io.github.sahilmakkar.chronos.task.TaskChain
import io.github.sahilmakkar.chronos.task.policy.UniqueTaskPolicy
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import io.github.sahilmakkar.chronos.task.request.PeriodicTaskRequest
import io.github.sahilmakkar.chronos.task.request.TaskRequest

/**
 * Internal architecture port defining operations for persisting and scheduling task requests.
 *
 * Responsibilities include translating high-level task specifications into persistent entities,
 * resolving unique task naming collisions according to [UniqueTaskPolicy], persisting chain
 * dependency graphs in single atomic database transactions, recalibrating operating system wake
 * triggers via [io.github.sahilmakkar.chronos.scheduler.WakeScheduler], and kicking the coordinator
 * to evaluate ready tasks without unnecessary latency.
 */
internal interface TaskEnqueuer {

    /**
     * Enqueues a unique one-time task applying the specified collision [policy].
     *
     * If an existing task shares [name]:
     * - [UniqueTaskPolicy.KEEP]: The existing task continues unaffected; the new request is ignored.
     * - [UniqueTaskPolicy.REPLACE]: The existing active task and its dependents are cancelled; the new request is enqueued.
     * - [UniqueTaskPolicy.APPEND]: The new request is enqueued as a dependent child appended to the end of the existing chain.
     *
     * @param name Unique identifier name used to detect collisions across multiple enqueues.
     * @param policy Collision resolution strategy if a task with [name] already exists in the database.
     * @param request The one-time task specification to enqueue.
     */
    fun enqueueTask(name: String, policy: UniqueTaskPolicy, request: OneTimeTaskRequest)

    /**
     * Enqueues a unique periodic task applying the specified collision [policy].
     *
     * Note that [UniqueTaskPolicy.APPEND] is not supported for periodic tasks and will throw an
     * [IllegalArgumentException].
     *
     * @param name Unique identifier name used to detect collisions across multiple enqueues.
     * @param policy Collision resolution strategy ([UniqueTaskPolicy.KEEP] or [UniqueTaskPolicy.REPLACE]).
     * @param request The periodic task specification to enqueue.
     * @throws IllegalArgumentException if [policy] is [UniqueTaskPolicy.APPEND].
     */
    fun enqueuePeriodicTask(name: String, policy: UniqueTaskPolicy, request: PeriodicTaskRequest)

    /**
     * Enqueues a sequential task chain in a single atomic database transaction.
     *
     * All tasks in the chain are inserted simultaneously with their dependency edges and failure
     * propagation policies. If the chain is empty, this operation is a no-op.
     *
     * @param chain The task chain containing sequential requests and dependency policies.
     */
    fun enqueue(chain: TaskChain)
}
