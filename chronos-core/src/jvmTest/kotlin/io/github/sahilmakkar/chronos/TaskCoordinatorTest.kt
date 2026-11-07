package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.engine.TaskCoordinator
import io.github.sahilmakkar.chronos.executor.InProcessTaskExecutor
import io.github.sahilmakkar.chronos.scheduler.ChronosTaskCanceller
import io.github.sahilmakkar.chronos.scheduler.ChronosTaskEnqueuer
import io.github.sahilmakkar.chronos.storage.TaskEntity
import io.github.sahilmakkar.chronos.storage.mapper.toEntity
import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.TaskState
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.fromJson
import io.github.sahilmakkar.chronos.task.model.taskDataOf
import io.github.sahilmakkar.chronos.task.model.toJson
import io.github.sahilmakkar.chronos.task.policy.BackoffPolicy
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import io.github.sahilmakkar.chronos.task.request.PeriodicTaskRequest
import io.github.sahilmakkar.chronos.task.request.TaskRequest
import io.github.sahilmakkar.chronos.worker.TaskWorker
import io.github.sahilmakkar.chronos.worker.WorkerRegistry
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
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
        h.clock.advance(10_000L)
        h.runPendingTasks()
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

    class HangingWorker(data: TaskData) : TaskWorker(data) {
        override suspend fun doWork(): TaskResult {
            hangingStarted?.complete(Unit)
            hangingBlocker?.await()
            return TaskResult.Success()
        }

        companion object {
            var hangingStarted: kotlinx.coroutines.CompletableDeferred<Unit>? = null
            var hangingBlocker: kotlinx.coroutines.CompletableDeferred<Unit>? = null
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
        val output = TaskData.fromJson(entity?.outputDataJson ?: "")
        assertEquals("Task timed out after 50ms", output.getString("error"))
    }

    @Test
    fun `closing Chronos while slow worker is running leaves task in enqueued state`() = runTest {
        val slowStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val slowBlocker = kotlinx.coroutines.CompletableDeferred<Unit>()
        HangingWorker.hangingStarted = slowStarted
        HangingWorker.hangingBlocker = slowBlocker

        val tempDb = File.createTempFile("chronos_close_test_", ".db").apply { deleteOnExit() }
        val testDb = createTestDatabase(tempDb)

        val registry = WorkerRegistry().apply {
            register(HangingWorker::class.qualifiedName!!) { HangingWorker(it) }
        }
        val realExecutor = InProcessTaskExecutor(testDb, registry)
        val coordinator = io.github.sahilmakkar.chronos.engine.TaskCoordinator(
            db = testDb,
            taskExecutor = realExecutor,
            constraintProvider = h.constraintVerifier,
            clock = h.clock
        )
        val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val daemon = io.github.sahilmakkar.chronos.engine.Daemon(
            coroutineScope = engineScope,
            coordinator = coordinator,
            db = testDb
        )
        val enqueuer = ChronosTaskEnqueuer(
            db = testDb,
            coroutineScope = engineScope,
            coordinator = coordinator,
            clock = h.clock
        )
        val canceller = ChronosTaskCanceller(
            db = testDb,
            coroutineScope = engineScope
        )
        val engine = io.github.sahilmakkar.chronos.engine.ChronosEngine(
            registry = registry,
            coroutineScope = engineScope,
            coordinator = coordinator,
            daemon = daemon,
            enqueuer = enqueuer,
            canceller = canceller,
            taskStore = io.github.sahilmakkar.chronos.storage.RoomTaskStore(testDb.taskDao()),
            onClose = { testDb.close() }
        )

        val req = OneTimeTaskRequest.Builder(HangingWorker::class).build()
        engine.enqueue(req)

        slowStarted.await()
        assertEquals(TaskState.RUNNING, testDb.taskDao().getTask(req.id)?.state)

        engine.close()
        engineScope.coroutineContext[kotlinx.coroutines.Job]?.children?.toList()?.forEach { it.join() }

        val dbCheck = createTestDatabase(tempDb)
        val entity = dbCheck.taskDao().getTask(req.id)
        assertEquals(TaskState.ENQUEUED, entity?.state)
        dbCheck.close()
        tempDb.delete()
        File("${tempDb.absolutePath}-wal").delete()
        File("${tempDb.absolutePath}-shm").delete()
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
    fun `explicit cancelTaskById on periodic task transitions to cancelled and does not run again`() = runTest {
        val interval = 15.minutes
        val req = PeriodicTaskRequest.Builder(DummyWorker::class, repeatInterval = interval).build()

        h.enqueue(req)
        h.cancelTaskById(req.id)

        val entity = h.db.taskDao().getTask(req.id)
        assertEquals(TaskState.CANCELLED, entity?.state)

        val executedBefore = h.executor.executed.size
        h.clock.advance(interval.inWholeMilliseconds)
        h.runPendingTasks()

        assertEquals(executedBefore, h.executor.executed.size)
        assertEquals(TaskState.CANCELLED, h.db.taskDao().getTask(req.id)?.state)
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

    @Test
    fun `tasks waiting for semaphore permit remain ENQUEUED until permit acquired`() = runTest {
        val testDb = createTestDatabase(File.createTempFile("sem_test_", ".db").apply { deleteOnExit() })
        val t1Started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val t1Proceed = kotlinx.coroutines.CompletableDeferred<Unit>()

        val testExecutor = object : io.github.sahilmakkar.chronos.port.TaskExecutor {
            override suspend fun execute(entity: TaskEntity, inputData: TaskData): TaskResult {
                if (entity.id == "t1") {
                    t1Started.complete(Unit)
                    t1Proceed.await()
                }
                return TaskResult.Success()
            }
        }

        val testCoordinator = TaskCoordinator(
            db = testDb,
            taskExecutor = testExecutor,
            constraintProvider = FakeConstraintVerifier(),
            clock = h.clock,
            maxConcurrentTasks = 1
        )

        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val t1 = h.request().toEntity(scheduledAt = h.clock.nowMs).copy(id = "t1")
            testDb.taskDao().insertTask(t1)

            val runJob1 = testScope.launch {
                testCoordinator.runPendingTasks()
            }
            t1Started.await()
            assertEquals(TaskState.RUNNING, testDb.taskDao().getTask("t1")?.state)

            val t2 = h.request().toEntity(scheduledAt = h.clock.nowMs).copy(id = "t2")
            testDb.taskDao().insertTask(t2)

            val runJob2 = testScope.launch {
                testCoordinator.runPendingTasks()
            }

            withContext(Dispatchers.Default) { delay(200) }

            assertEquals(TaskState.ENQUEUED, testDb.taskDao().getTask("t2")?.state)

            t1Proceed.complete(Unit)
            runJob1.join()
            runJob2.join()

            assertEquals(TaskState.SUCCEEDED, testDb.taskDao().getTask("t1")?.state)
            assertEquals(TaskState.SUCCEEDED, testDb.taskDao().getTask("t2")?.state)
        } finally {
            t1Proceed.complete(Unit)
            testScope.cancel()
            testDb.close()
        }
    }

    @Test
    fun `cancelled task in flight remains CANCELLED and is not reset to ENQUEUED`() = runTest {
        val testDb = createTestDatabase(File.createTempFile("cancel_test_", ".db").apply { deleteOnExit() })
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val pause = kotlinx.coroutines.CompletableDeferred<Unit>()

        val testExecutor = object : io.github.sahilmakkar.chronos.port.TaskExecutor {
            override suspend fun execute(entity: TaskEntity, inputData: TaskData): TaskResult {
                started.complete(Unit)
                pause.await()
                return TaskResult.Success()
            }
        }

        val testCoordinator = TaskCoordinator(
            db = testDb,
            taskExecutor = testExecutor,
            constraintProvider = FakeConstraintVerifier(),
            clock = h.clock
        )

        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val req = h.request().toEntity(scheduledAt = h.clock.nowMs)
            testDb.taskDao().insertTask(req)

            val runJob = testScope.launch {
                testCoordinator.runPendingTasks()
            }

            started.await()
            assertEquals(TaskState.RUNNING, testDb.taskDao().getTask(req.id)?.state)

            testDb.taskDao().cancelTaskIfActive(req.id)
            assertEquals(TaskState.CANCELLED, testDb.taskDao().getTask(req.id)?.state)

            runJob.cancel()
            runJob.join()

            assertEquals(TaskState.CANCELLED, testDb.taskDao().getTask(req.id)?.state)
        } finally {
            pause.complete(Unit)
            testScope.cancel()
            testDb.close()
        }
    }
}