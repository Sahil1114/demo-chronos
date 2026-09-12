package io.github.sahilmakkar.chronos.testing

import io.github.sahilmakkar.chronos.*
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.TaskInfo
import io.github.sahilmakkar.chronos.task.model.workDataOf
import io.github.sahilmakkar.chronos.kmpWorker.WorkerRegistry
import io.github.sahilmakkar.chronos.task.constraints.PlatformCapabilities
import io.github.sahilmakkar.chronos.task.policy.UniqueTaskPolicy
import io.github.sahilmakkar.chronos.task.TaskChain
import io.github.sahilmakkar.chronos.network.NetworkChecker
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import io.github.sahilmakkar.chronos.task.request.PeriodicTaskRequest
import io.github.sahilmakkar.chronos.task.request.TaskRequest
import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.TaskState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlin.time.Duration

/**
 * A mock [io.github.sahilmakkar.chronos.utils.KmpClock] implementation allowing manual control over current time progression in unit tests.
 *
 * @property currentTime The current time instant, initialized to epoch start.
 */
class FakeClock(var currentTime: Instant = Instant.fromEpochMilliseconds(0)) : KmpClock {
    
    /**
     * Advances the fake clock time by the specified [duration].
     */
    fun advanceBy(duration: Duration) { currentTime += duration }
    
    override fun now(): Instant = currentTime
}

/**
 * A mock, synchronous implementation of [Chronos] designed for isolated testing of workers
 * without database or system background scheduling dependencies.
 *
 * @property registry The worker registry mapped to worker implementation classes.
 * @property clock The mock clock implementation.
 */
class TestChronos(
    override val registry: WorkerRegistry = WorkerRegistry(),
    override val clock: KmpClock = FakeClock()
) : Chronos(registry, clock) {
    val enqueuedRequests = mutableListOf<TaskRequest>()
    private val workInfos = MutableStateFlow<Map<String, TaskInfo>>(emptyMap())

    override val networkChecker: NetworkChecker = object : NetworkChecker {
        override fun isConnected(): Boolean = true
        override fun isUnmetered(): Boolean = true
    }

    override fun enqueue(request: TaskRequest) {
        enqueuedRequests.add(request)
        updateWorkInfo(request.id, TaskState.ENQUEUED)
    }

    override fun enqueueUniqueWork(name: String, policy: UniqueTaskPolicy, request: OneTimeTaskRequest) {
        enqueue(request)
    }

    override fun enqueueUniquePeriodicWork(name: String, policy: UniqueTaskPolicy, request: PeriodicTaskRequest) {
        enqueue(request)
    }

    override fun enqueue(chain: TaskChain) {
        chain.requests.forEach { enqueue(it) }
    }

    override fun cancelWorkById(id: String) {
        updateWorkInfo(id, TaskState.CANCELLED)
    }

    override fun cancelWorkByTag(tag: String) {
        // Tag cancellation not fully fleshed in stub
    }

    override fun cancelAllWork() {
        workInfos.value.keys.forEach { cancelWorkById(it) }
    }

    override fun getWorkInfoFlow(id: String): Flow<TaskInfo?> {
        return workInfos.map { it[id] }
    }

    override fun getWorkInfosByTagFlow(tag: String): Flow<List<TaskInfo>> {
        return workInfos.map { it.values.toList() } // Stub
    }

    override fun platformCapabilities(): PlatformCapabilities = PlatformCapabilities(
        periodicTasksGuaranteed = true,
        initialDelayPrecise = true,
        backgroundExecutionReliable = true,
        networkConstraintNative = true,
        deviceIdleSupported = true
    )

    override suspend fun runPendingTasksNow() {
        // Synchronously run enqueued requests for testing
        val iterator = enqueuedRequests.iterator()
        while (iterator.hasNext()) {
            val req = iterator.next()
            updateWorkInfo(req.id, TaskState.RUNNING)
            try {
                val worker = registry.createWorker(req.workerClassName, req.taskData)
                val res = worker.doWork()
                when (res) {
                    is TaskResult.Success -> updateWorkInfo(req.id, TaskState.SUCCEEDED, res.outputData)
                    is TaskResult.Failure -> updateWorkInfo(req.id, TaskState.FAILED, res.outputData)
                    TaskResult.Retry -> updateWorkInfo(req.id, TaskState.RETRYING)
                }
            } catch (e: Exception) {
                updateWorkInfo(req.id, TaskState.FAILED)
            }
            iterator.remove()
        }
    }

    private fun updateWorkInfo(id: String, state: TaskState, output: TaskData = workDataOf()) {
        val current = workInfos.value[id]
        val attempt = (current?.runAttemptCount ?: 0) + (if (state == TaskState.RUNNING) 1 else 0)
        val newInfo = TaskInfo(
            id = id,
            state = state,
            outputData = output,
            progress = current?.progress ?: workDataOf(),
            runAttemptCount = attempt
        )
        workInfos.value = workInfos.value + (id to newInfo)
    }
}
