package io.github.sahilmakkar.chronos.testing

import io.github.sahilmakkar.chronos.Chronos
import io.github.sahilmakkar.chronos.task.TaskChain
import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.TaskState
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.TaskInfo
import io.github.sahilmakkar.chronos.task.model.taskDataOf
import io.github.sahilmakkar.chronos.task.policy.UniqueTaskPolicy
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import io.github.sahilmakkar.chronos.task.request.PeriodicTaskRequest
import io.github.sahilmakkar.chronos.task.request.TaskRequest
import io.github.sahilmakkar.chronos.worker.WorkerRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Fully in-memory, deterministic implementation of [Chronos] designed for unit and integration testing.
 *
 * `TestChronos` eliminates the need for filesystem SQLite databases, native background daemons,
 * or OS-level schedulers (WorkManager on Android or BGTaskScheduler on iOS). All enqueued task requests
 * are buffered in memory and will NOT execute automatically in the background; instead, test authors
 * exert deterministic control over task execution by calling [runPendingTasksNow].
 *
 * ### Integration with Global Instance
 * Install `TestChronos` into the global registry during test setup using [Chronos.Companion.setForTesting]:
 * ```kotlin
 * @BeforeTest
 * fun setUp() {
 *     val registry = WorkerRegistry().apply {
 *         register("UploadWorker") { UploadWorker(it) }
 *     }
 *     val testChronos = TestChronos(registry = registry)
 *     Chronos.setForTesting(testChronos)
 * }
 * ```
 *
 * ### Verifying Enqueued Tasks
 * Enqueued requests are recorded in [enqueuedRequests]:
 * ```kotlin
 * val request = OneTimeTaskRequest.Builder("UploadWorker")
 *     .addTag("sync")
 *     .build()
 * chronos.enqueue(request)
 *
 * assertEquals(1, testChronos.enqueuedRequests.size)
 * assertEquals("UploadWorker", testChronos.enqueuedRequests.first().workerClassName)
 * ```
 *
 * ### Simulating Execution and Observing Results
 * Trigger pending tasks synchronously and observe reactive flow updates:
 * ```kotlin
 * val flow = testChronos.observeTask(request.id)
 * assertEquals(TaskState.ENQUEUED, flow.first()?.state)
 *
 * testChronos.runPendingTasksNow()
 *
 * assertEquals(TaskState.SUCCEEDED, flow.first()?.state)
 * ```
 *
 * @property registry Worker registry instance used to instantiate workers during [runPendingTasksNow].
 */
public class TestChronos(
    public override val registry: WorkerRegistry = WorkerRegistry(),
) : Chronos {

    /**
     * In-memory buffer of all currently pending, unexecuted [TaskRequest] instances.
     *
     * Requests are added upon calling [enqueue] and removed as they are executed during [runPendingTasksNow].
     */
    public val enqueuedRequests: MutableList<TaskRequest> = mutableListOf<TaskRequest>()

    private val taskTags = mutableMapOf<String, Set<String>>()
    private val taskInfos = MutableStateFlow<Map<String, TaskInfo>>(emptyMap())

    /**
     * Enqueues a task request into the in-memory test queue.
     *
     * Adds [request] to [enqueuedRequests], associates its tags, and publishes an initial [TaskInfo]
     * snapshot in [TaskState.ENQUEUED] to reactive observation flows.
     *
     * @param request The task specification to enqueue.
     */
    public override fun enqueue(request: TaskRequest) {
        enqueuedRequests.add(request)
        taskTags[request.id] = request.tags
        updateTaskInfo(request.id, TaskState.ENQUEUED)
    }

    /**
     * Enqueues a unique one-time task into the in-memory test queue.
     *
     * In this in-memory test double, delegates directly to [enqueue].
     *
     * @param name Unique task name.
     * @param policy Collision resolution policy.
     * @param request The one-time task request to enqueue.
     */
    public override fun enqueueUniqueTask(name: String, policy: UniqueTaskPolicy, request: OneTimeTaskRequest) {
        enqueue(request = request)
    }

    /**
     * Enqueues a unique periodic task into the in-memory test queue.
     *
     * In this in-memory test double, delegates directly to [enqueue].
     *
     * @param name Unique task name.
     * @param policy Collision resolution policy.
     * @param request The periodic task request to enqueue.
     */
    public override fun enqueueUniquePeriodicTask(name: String, policy: UniqueTaskPolicy, request: PeriodicTaskRequest) {
        enqueue(request = request)
    }

    /**
     * Enqueues all requests belonging to a sequential [TaskChain].
     *
     * Iterates through [TaskChain.requests] and enqueues each request individually into memory.
     *
     * @param chain The task chain containing sequential requests.
     */
    public override fun enqueue(chain: TaskChain) {
        chain.requests.forEach { enqueue(request = it) }
    }

    /**
     * Cancels an enqueued or running task identified by [id].
     *
     * Transitions the matching task's in-memory status to [TaskState.CANCELLED] and emits
     * the updated snapshot to all active observers.
     *
     * @param id The canonical UUID string of the task to cancel.
     */
    public override fun cancelTaskById(id: String) {
        updateTaskInfo(id = id, state = TaskState.CANCELLED)
    }

    /**
     * Cancels all tasks currently carrying the specified [tag].
     *
     * Finds every task associated with [tag] and transitions its state to [TaskState.CANCELLED].
     *
     * @param tag Tag grouping string.
     */
    public override fun cancelTaskByTag(tag: String) {
        val idsToCancel = taskTags.filter { (_, tags) -> tag in tags }.keys
        idsToCancel.forEach { cancelTaskById(it) }
    }

    /**
     * Cancels all tracked tasks in the in-memory store.
     *
     * Transitions every known task to [TaskState.CANCELLED].
     */
    public override fun cancelAllTasks() {
        taskInfos.value.keys.forEach { cancelTaskById(id = it) }
    }

    /**
     * Returns a reactive [Flow] streaming state updates for a specific task.
     *
     * Emits the current [TaskInfo] snapshot immediately, followed by new snapshots whenever the task's
     * state, progress, or output data is modified. Emits `null` if the task does not exist.
     *
     * @param id The canonical UUID string of the task to observe.
     * @return A cold [Flow] emitting the task snapshot or `null`.
     */
    public override fun observeTask(id: String): Flow<TaskInfo?> {
        return taskInfos.map { it[id] }
    }

    /**
     * Returns a reactive [Flow] streaming a list of state updates for all tasks bearing [tag].
     *
     * Emits an updated list whenever any task with [tag] is enqueued, updated, or cancelled.
     *
     * @param tag Tag identifier for querying tasks.
     * @return A cold [Flow] emitting lists of matching [TaskInfo] snapshots.
     */
    public override fun observeTasksByTag(tag: String): Flow<List<TaskInfo>> {
        return taskInfos.map { map ->
            val matchingIds = taskTags.filter { (_, tags) -> tag in tags }.keys
            matchingIds.mapNotNull { map[it] }
        }
    }

    /**
     * Synchronously executes all currently queued tasks in [enqueuedRequests] on the caller coroutine.
     *
     * For each pending task:
     * 1. Transitions the task state to [TaskState.RUNNING] and increments [TaskInfo.runAttemptCount].
     * 2. Resolves and instantiates the worker via [registry].
     * 3. Invokes [io.github.sahilmakkar.chronos.worker.TaskWorker.doWork].
     * 4. Updates the task state to [TaskState.SUCCEEDED] (with output payload), [TaskState.FAILED]
     *    (with failure output or exception details), or [TaskState.RETRYING].
     * 5. Removes the executed request from [enqueuedRequests].
     */
    public override suspend fun runPendingTasksNow() {
        val iterator = enqueuedRequests.iterator()
        while (iterator.hasNext()) {
            val req = iterator.next()
            updateTaskInfo(req.id, TaskState.RUNNING)
            try {
                val worker = registry.createWorker(req.workerClassName, req.taskData)
                val res = worker.doWork()
                when (res) {
                    is TaskResult.Success -> updateTaskInfo(
                        id = req.id,
                        state = TaskState.SUCCEEDED,
                        output = res.outputData
                    )
                    is TaskResult.Failure -> updateTaskInfo(
                        id = req.id,
                        state = TaskState.FAILED,
                        output = res.outputData
                    )
                    TaskResult.Retry -> updateTaskInfo(id = req.id, state = TaskState.RETRYING)
                }
            } catch (e: Exception) {
                updateTaskInfo(id = req.id, state = TaskState.FAILED)
            }
            iterator.remove()
        }
    }

    /**
     * Closes the test instance, clearing all buffered requests, tag maps, and in-memory task states.
     */
    public override fun close() {
        enqueuedRequests.clear()
        taskTags.clear()
        taskInfos.value = emptyMap()
    }

    /**
     * Helper to update the in-memory [TaskInfo] snapshot for [id] and emit it through [taskInfos].
     */
    private fun updateTaskInfo(id: String, state: TaskState, output: TaskData = taskDataOf()) {
        val current = taskInfos.value[id]
        val attempt = (current?.runAttemptCount ?: 0) + (if (state == TaskState.RUNNING) 1 else 0)
        val newInfo = TaskInfo(
            id = id,
            state = state,
            outputData = output,
            progress = current?.progress ?: taskDataOf(),
            runAttemptCount = attempt
        )
        taskInfos.value += (id to newInfo)
    }
}
