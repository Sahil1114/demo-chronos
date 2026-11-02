package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.engine.TaskCoordinator
import io.github.sahilmakkar.chronos.storage.mapper.toEntity
import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.TaskState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoordinatorConcurrencyTest {

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
    fun `two concurrent runPendingTasks plus enqueue triggers exactly one execution per task id`() = runTest {
        val t1 = h.request()
        val t2 = h.request()
        h.executor.script = {
            delay(20)
            TaskResult.Success()
        }
        h.db.taskDao().insertTask(t1.toEntity(scheduledAt = h.clock.nowMs))

        val j1 = launch(Dispatchers.Default) { h.coordinator.runPendingTasks() }
        val j2 = launch(Dispatchers.Default) { h.coordinator.runPendingTasks() }
        val j3 = launch(Dispatchers.Default) {
            h.enqueuer.enqueue(t2)
        }
        joinAll(j1, j2, j3)
        h.coordinator.runPendingTasks()

        val occurrences = h.executor.executed.groupingBy { it }.eachCount()
        for ((id, count) in occurrences) {
            assertEquals(1, count, "Task $id was executed more than once: $count")
        }
    }

    @Test
    fun `twenty tasks enqueued at once execute once and max concurrency is respected`() = runTest {
        val executor = FakeTaskExecutor()
        executor.script = {
            delay(30)
            TaskResult.Success()
        }
        val coordinator = TaskCoordinator(
            db = h.db,
            taskExecutor = executor,
            constraintProvider = h.constraintVerifier,
            clock = h.clock,
            maxConcurrentTasks = 4
        )
        val requests = (1..20).map { h.request() }
        requests.forEach { req ->
            h.db.taskDao().insertTask(req.toEntity(scheduledAt = h.clock.nowMs))
        }

        withContext(Dispatchers.Default) {
            coordinator.runPendingTasks()
        }

        assertEquals(20, executor.executed.size)
        assertEquals(20, executor.executed.distinct().size)
        assertTrue(executor.peakConcurrency.get() <= 4, "Peak concurrency was ${executor.peakConcurrency.get()}")
        assertTrue(executor.peakConcurrency.get() > 1, "Concurrency should be > 1, was ${executor.peakConcurrency.get()}")
    }

    @Test
    fun `task whose worker throws does not stop other tasks in same pass`() = runTest {
        val t1 = h.request()
        val t2 = h.request()
        h.executor.script = { entity ->
            if (entity.id == t1.id) {
                throw IllegalStateException("Worker threw unexpected exception")
            } else {
                TaskResult.Success()
            }
        }
        h.db.taskDao().insertTask(t1.toEntity(scheduledAt = h.clock.nowMs))
        h.db.taskDao().insertTask(t2.toEntity(scheduledAt = h.clock.nowMs))

        h.coordinator.runPendingTasks()

        assertEquals(TaskState.FAILED, h.db.taskDao().getTask(t1.id)?.state)
        assertEquals(TaskState.SUCCEEDED, h.db.taskDao().getTask(t2.id)?.state)
        assertTrue(h.executor.executed.contains(t1.id))
        assertTrue(h.executor.executed.contains(t2.id))
    }

    @Test
    fun `long-running task does not delay unrelated short task when maxConcurrentTasks greater than 1`() = runTest {
        val longTask = h.request()
        val shortTask = h.request()

        val longStarted = CompletableDeferred<Unit>()
        val shortFinished = CompletableDeferred<Unit>()

        h.executor.script = { entity ->
            if (entity.id == longTask.id) {
                longStarted.complete(Unit)
                shortFinished.await()
                TaskResult.Success()
            } else {
                TaskResult.Success()
            }
        }

        h.db.taskDao().insertTask(longTask.toEntity(scheduledAt = h.clock.nowMs))
        val longJob = launch(Dispatchers.Default) {
            h.coordinator.runPendingTasks()
        }

        longStarted.await()

        h.db.taskDao().insertTask(shortTask.toEntity(scheduledAt = h.clock.nowMs))
        val shortJob = launch(Dispatchers.Default) {
            h.coordinator.runPendingTasks()
        }

        shortJob.join()
        assertEquals(TaskState.SUCCEEDED, h.db.taskDao().getTask(shortTask.id)?.state)
        shortFinished.complete(Unit)
        longJob.join()

        assertEquals(TaskState.SUCCEEDED, h.db.taskDao().getTask(longTask.id)?.state)
    }
}
