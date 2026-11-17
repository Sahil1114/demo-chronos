package io.github.sahilmakkar.chronos.scheduler

import io.github.sahilmakkar.chronos.engine.TaskCoordinator
import io.github.sahilmakkar.chronos.port.TaskEnqueuer
import io.github.sahilmakkar.chronos.storage.ChronosDatabase
import io.github.sahilmakkar.chronos.storage.TaskDependencyEntity
import io.github.sahilmakkar.chronos.storage.TaskEntity
import io.github.sahilmakkar.chronos.storage.TaskTagEntity
import io.github.sahilmakkar.chronos.storage.mapper.toEntity
import io.github.sahilmakkar.chronos.task.TaskChain
import io.github.sahilmakkar.chronos.task.TaskState
import io.github.sahilmakkar.chronos.task.policy.DependentTaskPolicy
import io.github.sahilmakkar.chronos.task.policy.ExistingTaskPolicy
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import io.github.sahilmakkar.chronos.task.request.PeriodicTaskRequest
import io.github.sahilmakkar.chronos.task.request.TaskRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Default implementation of [TaskEnqueuer] managing database task insertions and execution triggers.
 *
 * Persists task requests, schedules platform wake-ups via [WakeScheduler], and notifies [TaskCoordinator]
 * to evaluate pending work immediately.
 *
 * A task name always refers to a single generation of tasks: when a new generation starts
 * (KEEP with nothing active, or REPLACE), older rows give up the name.
 */
internal class ChronosTaskEnqueuer(
    private val db: ChronosDatabase,
    private val coroutineScope: CoroutineScope,
    private val coordinator: TaskCoordinator,
    private val clock: Clock = Clock.System,
    private val onEnqueued: ((TaskRequest) -> Unit)? = null,
    private val wakeScheduler: WakeScheduler? = null,
) : TaskEnqueuer {
    /**
     * Enqueues a named one-time task applying the specified [policy].
     *
     * "Active" means `ENQUEUED`, `RUNNING` or `RETRYING`.
     * - [ExistingTaskPolicy.KEEP]: If a task with [name] is active, drops [request]. Finished tasks do not block it.
     * - [ExistingTaskPolicy.REPLACE]: Cancels every active task with [name] (a running worker is not interrupted;
     *   its result is ignored), then inserts [request].
     * - [ExistingTaskPolicy.APPEND]: Attaches [request] behind the current leaf of the chain under [name].
     *   Runs after a `SUCCEEDED` leaf; behind a `FAILED` or `CANCELLED` leaf it fails or is cancelled
     *   (STOP_ON_FAILURE). If nothing holds [name], [request] is inserted as a new task.
     *
     * @param name Task name identifier.
     * @param policy Strategy for handling an existing task under the same name.
     * @param request One-time task specification to enqueue.
     */
    override fun enqueueTask(
        name: String,
        policy: ExistingTaskPolicy,
        request: OneTimeTaskRequest,
    ) {
        coroutineScope.launch {
            val dao = db.taskDao()
            val active = dao.getActiveTasksByName(name = name, states = ACTIVE_STATES)

            when (policy) {
                ExistingTaskPolicy.KEEP -> {
                    if (active.isNotEmpty()) return@launch // something is alive: drop the request
                }

                ExistingTaskPolicy.REPLACE -> {
                    active.forEach { dao.cancelTaskIfActive(id = it.id) }
                }

                ExistingTaskPolicy.APPEND -> {
                    val leaf = dao.getLeafByName(name = name)
                    if (leaf != null) {
                        insertWithDependency(
                            request = request,
                            uniqueName = name,
                            parentId = leaf.id,
                        )
                        onEnqueued?.invoke(request)
                        coordinator.sweepChainRecovery()
                        wakeScheduler?.scheduleNextWake()
                        coordinator.runPendingTasks()
                        return@launch
                    }
                    // nothing under this name: fall through and start a new one
                }
            }

            dao.detachName(name = name) // KEEP (nothing active) and REPLACE both start a new generation
            insertUnique(request = request, name = name)
            onEnqueued?.invoke(request)
            wakeScheduler?.scheduleNextWake()
            coordinator.runPendingTasks()
        }
    }

    /**
     * Enqueues a named periodic task applying [ExistingTaskPolicy.KEEP] or [ExistingTaskPolicy.REPLACE].
     *
     * A periodic task returns to `ENQUEUED` after each run, so it stays active until cancelled.
     *
     * @param name Task name identifier.
     * @param policy Collision resolution strategy. Note: [ExistingTaskPolicy.APPEND] is rejected.
     * @param request Periodic task specification to enqueue.
     * @throws IllegalArgumentException if [policy] is [ExistingTaskPolicy.APPEND].
     */
    override fun enqueuePeriodicTask(
        name: String,
        policy: ExistingTaskPolicy,
        request: PeriodicTaskRequest,
    ) {
        require(value = policy != ExistingTaskPolicy.APPEND) {
            "APPEND policy is not supported for periodic work."
        }
        coroutineScope.launch {
            val dao = db.taskDao()
            val active = dao.getActiveTasksByName(name = name, states = ACTIVE_STATES)

            when (policy) {
                ExistingTaskPolicy.KEEP -> {
                    if (active.isNotEmpty()) return@launch // already scheduled: drop the request
                }

                ExistingTaskPolicy.REPLACE -> {
                    active.forEach { dao.cancelTaskIfActive(id = it.id) }
                }

                ExistingTaskPolicy.APPEND -> {
                    error("APPEND is rejected by require() above")
                }
            }

            dao.detachName(name = name)
            insertUnique(request = request, name = name)
            onEnqueued?.invoke(request)
            wakeScheduler?.scheduleNextWake()
            coordinator.runPendingTasks()
        }
    }

    /**
     * Enqueues a sequential [TaskChain] within a single atomic database transaction.
     *
     * Tasks, tags, and parent-child dependency edges are persisted simultaneously.
     *
     * @param chain The chained sequence of task requests.
     */
    override fun enqueue(chain: TaskChain) {
        if (chain.requests.isEmpty()) return
        coroutineScope.launch {
            val nowMs = clock.now().toEpochMilliseconds()
            val tasks = mutableListOf<TaskEntity>()
            val tags = mutableListOf<TaskTagEntity>()
            val dependencies = mutableListOf<TaskDependencyEntity>()

            chain.requests.forEachIndexed { i, req ->
                tasks.add(
                    req.toEntity(
                        scheduledAt = nowMs + req.initialDelay.inWholeMilliseconds,
                    ),
                )
                tags.addAll(req.tags.map { TaskTagEntity(taskId = req.id, tag = it) })
                if (i > 0) {
                    dependencies.add(
                        TaskDependencyEntity(
                            parentTaskId = chain.requests[i - 1].id,
                            childTaskId = req.id,
                            chainFailurePolicy = chain.failurePolicies[i - 1],
                        ),
                    )
                }
            }
            db.taskDao().insertChain(tasks = tasks, tags = tags, dependencies = dependencies)
            chain.requests.forEach { onEnqueued?.invoke(it) }
            wakeScheduler?.scheduleNextWake()
            coordinator.runPendingTasks()
        }
    }

    private suspend fun insertUnique(
        request: TaskRequest,
        name: String,
    ) {
        db.taskDao().insertTask(
            task =
                request.toEntity(
                    scheduledAt =
                        clock
                            .now()
                            .toEpochMilliseconds() + request.initialDelay.inWholeMilliseconds,
                    uniqueName = name,
                ),
        )
        db
            .taskDao()
            .insertTags(tags = request.tags.map { TaskTagEntity(taskId = request.id, tag = it) })
    }

    private suspend fun insertWithDependency(
        request: TaskRequest,
        uniqueName: String,
        parentId: String,
    ) {
        val task =
            request.toEntity(
                scheduledAt = clock.now().toEpochMilliseconds() + request.initialDelay.inWholeMilliseconds,
                uniqueName = uniqueName,
            )

        val tags = request.tags.map { TaskTagEntity(taskId = request.id, tag = it) }

        val dependency =
            TaskDependencyEntity(
                parentTaskId = parentId,
                childTaskId = request.id,
                chainFailurePolicy = DependentTaskPolicy.STOP_ON_FAILURE,
            )

        db.taskDao().insertChain(tasks = listOf(task), tags = tags, dependencies = listOf(dependency))
    }

    private companion object {
        // Must match what TaskDao.cancelTaskIfActive treats as active (everything except the terminal states).
        val ACTIVE_STATES = listOf(TaskState.ENQUEUED, TaskState.RUNNING, TaskState.RETRYING)
    }
}
