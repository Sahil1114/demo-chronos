package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.executor.InProcessTaskExecutor
import io.github.sahilmakkar.chronos.storage.mapper.toEntity
import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.TaskState
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.fromJson
import io.github.sahilmakkar.chronos.task.model.taskDataOf
import io.github.sahilmakkar.chronos.task.model.toJson
import io.github.sahilmakkar.chronos.task.policy.BackoffPolicy
import io.github.sahilmakkar.chronos.task.request.PeriodicTaskRequest
import io.github.sahilmakkar.chronos.task.request.TaskRequest
import io.github.sahilmakkar.chronos.worker.TaskWorker
import io.github.sahilmakkar.chronos.worker.WorkerRegistry
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TaskCoordinatorTest {

    private lateinit var h: TestHarness

    @BeforeTest
    fun setUp() {
        h = TestHarness()
    }

    @AfterTest
    fun tearDown() = runBlocking {
        h.close()
    }

    @Test
    fun `pending task transitions to running and succeeds`() = runTest {
        val req = h.request()
        h.executor.script = { TaskResult.Success(taskDataOf("done" to true)) }

        h.enqueue(req)
        h.runPendingTasks()

        val entity = h.db.taskDao().getTask(req.id)
        assertEquals(TaskState.SUCCEEDED, entity?.state)
        assertEquals(true, TaskData.fromJson(entity?.outputDataJson ?: "").getBoolean("done", false))
        assertEquals(listOf(req.id), h.executor.executed)
    }

    @Test
    fun `failure marks task failed with output data`() = runTest {
        val req = h.request()
        h.executor.script = { TaskResult.Failure(taskDataOf("error" to "something went wrong")) }

        h.enqueue(req)
        h.runPendingTasks()

        val entity = h.db.taskDao().getTask(req.id)
        assertEquals(TaskState.FAILED, entity?.state)
        assertEquals("something went wrong", TaskData.fromJson(entity?.outputDataJson ?: "").getString("error"))
    }

    @Test
    fun `retry then success`() = runTest {
        val req = h.request()
        var attempts = 0
        h.executor.script = {
            attempts++
            if (attempts == 1) TaskResult.Retry else TaskResult.Success()
        }

        h.enqueue(req)
        h.runPendingTasks()

        var entity = h.db.taskDao().getTask(req.id)
        assertEquals(TaskState.RETRYING, entity?.state)

        h.clock.advance(60_000L)
        h.runPendingTasks()

        entity = h.db.taskDao().getTask(req.id)
        assertEquals(TaskState.SUCCEEDED, entity?.state)
        assertEquals(listOf(req.id, req.id), h.executor.executed)
    }

    @Test
    fun `retries exhausted marks task permanently failed`() = runTest {
        val req = io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest.Builder(DummyWorker::class)
            .setBackoffCriteria(BackoffPolicy.LINEAR, initialDelay = 1.seconds, maxRetries = 2)
            .build()

        h.executor.script = { TaskResult.Retry }
        h.enqueue(req)

        h.runPendingTasks()
        h.db.taskDao().updateRunAttemptCount(req.id, 1)
        h.clock.advance(10_000L)
        h.runPendingTasks()

        val entity = h.db.taskDao().getTask(req.id)
        assertEquals(TaskState.FAILED, entity?.state)
    }

    @Test
    fun `not run before scheduledAt`() = runTest {
        val req = h.request(initialDelay = 1.hours)
        h.enqueue(req)
        h.runPendingTasks()

        assertEquals(emptyList(), h.executor.executed)
        assertEquals(TaskState.ENQUEUED, h.db.taskDao().getTask(req.id)?.state)

        h.clock.advance(1.hours.inWholeMilliseconds)
        h.runPendingTasks()

        assertEquals(listOf(req.id), h.executor.executed)
        assertEquals(TaskState.SUCCEEDED, h.db.taskDao().getTask(req.id)?.state)
    }

    @Test
    fun `skipped while constraints unmet`() = runTest {
        val req = h.request()
        h.constraintVerifier.satisfied = false

        h.enqueue(req)
        h.runPendingTasks()

        assertEquals(emptyList(), h.executor.executed)
        assertEquals(TaskState.ENQUEUED, h.db.taskDao().getTask(req.id)?.state)

        h.constraintVerifier.satisfied = true
        h.runPendingTasks()

        assertEquals(listOf(req.id), h.executor.executed)
        assertEquals(TaskState.SUCCEEDED, h.db.taskDao().getTask(req.id)?.state)
    }

    class SlowWorker(data: TaskData) : TaskWorker(data) {
        override suspend fun doWork(): TaskResult {
            delay(1000.milliseconds)
            return TaskResult.Success()
        }
    }

    @Test
    fun `worker not registered marks task failed`() = runTest {
        val registry = WorkerRegistry()
        val realExecutor = InProcessTaskExecutor(h.db, registry)
        val coordinator = io.github.sahilmakkar.chronos.engine.TaskCoordinator(
            db = h.db,
            taskExecutor = realExecutor,
            constraintProvider = h.constraintVerifier,
            clock = h.clock
        )

        val req = h.request()
        h.db.taskDao().insertTask(
            req.toEntity(scheduledAt = h.clock.nowMs)
        )
        coordinator.runPendingTasks()

        val entity = h.db.taskDao().getTask(req.id)
        assertEquals(TaskState.FAILED, entity?.state)
    }

    @Test
    fun `task execution timeout marks task failed`() = runTest {
        val registry = WorkerRegistry().apply {
            register("io.github.sahilmakkar.chronos.TaskCoordinatorTest.SlowWorker") { SlowWorker(it) }
        }
        val realExecutor = InProcessTaskExecutor(h.db, registry)
        val coordinator = io.github.sahilmakkar.chronos.engine.TaskCoordinator(
            db = h.db,
            taskExecutor = realExecutor,
            constraintProvider = h.constraintVerifier,
            clock = h.clock
        )

        val req = io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest.Builder(SlowWorker::class)
            .setTimeout(50.milliseconds)
            .build()

        h.db.taskDao().insertTask(
            req.toEntity(scheduledAt = h.clock.nowMs)
        )
        coordinator.runPendingTasks()

        val entity = h.db.taskDao().getTask(req.id)
        assertEquals(TaskState.FAILED, entity?.state)
    }

    @Test
    fun `periodic task reschedules after success and attempt count resets`() = runTest {
        val interval = 15.minutes
        val req = PeriodicTaskRequest.Builder(DummyWorker::class, repeatInterval = interval).build()

        h.enqueue(req)
        h.runPendingTasks()

        assertEquals(1, h.executor.executed.size)
        var entity = h.db.taskDao().getTask(req.id)
        assertEquals(TaskState.ENQUEUED, entity?.state)
        assertEquals(0, entity?.runAttemptCount)
        assertEquals(h.clock.nowMs + interval.inWholeMilliseconds, entity?.scheduledAt)

        h.clock.advance(interval.inWholeMilliseconds)
        h.runPendingTasks()

        assertEquals(2, h.executor.executed.size)
        entity = h.db.taskDao().getTask(req.id)
        assertEquals(TaskState.ENQUEUED, entity?.state)
        assertEquals(0, entity?.runAttemptCount)
        assertEquals(h.clock.nowMs + interval.inWholeMilliseconds, entity?.scheduledAt)
    }

    @Test
    fun `periodic task failure does not kill schedule`() = runTest {
        val interval = 15.minutes
        val req = PeriodicTaskRequest.Builder(DummyWorker::class, repeatInterval = interval).build()

        h.executor.script = { TaskResult.Failure() }
        h.enqueue(req)
        h.runPendingTasks()

        assertEquals(1, h.executor.executed.size)
        val entity = h.db.taskDao().getTask(req.id)
        assertEquals(TaskState.ENQUEUED, entity?.state)
        assertEquals(0, entity?.runAttemptCount)
        assertEquals(h.clock.nowMs + interval.inWholeMilliseconds, entity?.scheduledAt)

        h.clock.advance(interval.inWholeMilliseconds)
        h.executor.script = { TaskResult.Success() }
        h.runPendingTasks()

        assertEquals(2, h.executor.executed.size)
        val nextEntity = h.db.taskDao().getTask(req.id)
        assertEquals(TaskState.ENQUEUED, nextEntity?.state)
    }

    @Test
    fun `running tasks reset to enqueued on sweepOrphans`() = runTest {
        val req = h.request()
        h.enqueue(req)
        h.db.taskDao().updateTaskState(req.id, TaskState.RUNNING)

        assertEquals(TaskState.RUNNING, h.db.taskDao().getTask(req.id)?.state)

        h.coordinator.sweepOrphans()

        assertEquals(TaskState.ENQUEUED, h.db.taskDao().getTask(req.id)?.state)

        h.runPendingTasks()
        assertEquals(TaskState.SUCCEEDED, h.db.taskDao().getTask(req.id)?.state)
    }

    @Test
    fun `chain recovery recovers un-triggered child when parent succeeded`() = runTest {
        val parent = h.request()
        val child = h.request()

        h.enqueueChain(parent, child)

        h.db.taskDao().updateTaskState(parent.id, TaskState.SUCCEEDED)
        h.db.taskDao().updateTaskState(child.id, TaskState.ENQUEUED)
        h.db.taskDao().updateScheduledAt(child.id, h.clock.nowMs + 100_000L)

        h.executor.executed.clear()

        h.coordinator.sweepChainRecovery()

        val childEntity = h.db.taskDao().getTask(child.id)
        assertEquals(h.clock.nowMs, childEntity?.scheduledAt)

        h.runPendingTasks()
        assertEquals(listOf(child.id), h.executor.executed)
    }

    @Test
    fun `task state and data survive database close and reopen`() = runTest {
        val sharedDbFile = File.createTempFile("chronos_recovery_", ".db")
        sharedDbFile.deleteOnExit()

        val db1 = createTestDatabase(sharedDbFile)
        val harness1 = TestHarness(clock = h.clock, executor = h.executor)
        val req = harness1.request()
        val input = taskDataOf("savedKey" to "persistedValue", "count" to 42)
        val entity = req.toEntity(scheduledAt = harness1.clock.nowMs)
            .copy(inputDataJson = input.toJson())

        db1.taskDao().insertTask(entity)
        db1.close()

        val db2 = createTestDatabase(sharedDbFile)
        val restored = db2.taskDao().getTask(req.id)

        assertEquals(req.id, restored?.id)
        val restoredData = TaskData.fromJson(restored?.inputDataJson ?: "")
        assertEquals("persistedValue", restoredData.getString("savedKey"))
        assertEquals(42, restoredData.getInt("count", 0))

        db2.close()
        sharedDbFile.delete()
        File("${sharedDbFile.absolutePath}-wal").delete()
        File("${sharedDbFile.absolutePath}-shm").delete()
    }
}
