package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.TaskState
import io.github.sahilmakkar.chronos.task.policy.BackoffPolicy
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class RetrySemanticsTest {

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
    fun `maxRetries 0 runs once and does not retry`() = runTest {
        val req = OneTimeTaskRequest.Builder(DummyWorker::class)
            .setBackoffCriteria(BackoffPolicy.LINEAR, initialDelay = 1.seconds, maxRetries = 0)
            .build()
        h.executor.script = { TaskResult.Retry }
        h.enqueue(req)
        h.runPendingTasks()

        assertEquals(1, h.executor.executed.size)
        val entity = h.db.taskDao().getTask(req.id)
        assertEquals(TaskState.FAILED, entity?.state)
    }

    @Test
    fun `maxRetries 2 runs 3 times total and then fails`() = runTest {
        val req = OneTimeTaskRequest.Builder(DummyWorker::class)
            .setBackoffCriteria(BackoffPolicy.LINEAR, initialDelay = 1.seconds, maxRetries = 2)
            .build()
        h.executor.script = { TaskResult.Retry }
        h.enqueue(req)

        // Run 1: initial run, attempt becomes 1, state RETRYING
        h.runPendingTasks()
        assertEquals(1, h.executor.executed.size)
        assertEquals(TaskState.RETRYING, h.db.taskDao().getTask(req.id)?.state)

        // Run 2: retry 1, attempt becomes 2, state RETRYING
        h.clock.advance(5_000L)
        h.runPendingTasks()
        assertEquals(2, h.executor.executed.size)
        assertEquals(TaskState.RETRYING, h.db.taskDao().getTask(req.id)?.state)

        // Run 3: retry 2, attempt becomes 3, state FAILED
        h.clock.advance(5_000L)
        h.runPendingTasks()
        assertEquals(3, h.executor.executed.size)
        val entity = h.db.taskDao().getTask(req.id)
        assertEquals(TaskState.FAILED, entity?.state)

        // Ensure no further executions
        h.clock.advance(5_000L)
        h.runPendingTasks()
        assertEquals(3, h.executor.executed.size)
    }

    @Test
    fun `linear backoff delay formula is backoffDelayMs times attempt`() {
        val baseDelay = 1000L
        assertEquals(1000L, BackoffPolicy.LINEAR.calculateDelay(baseDelay, attempt = 1))
        assertEquals(2000L, BackoffPolicy.LINEAR.calculateDelay(baseDelay, attempt = 2))
        assertEquals(3000L, BackoffPolicy.LINEAR.calculateDelay(baseDelay, attempt = 3))
    }

    @Test
    fun `exponential backoff delay formula is backoffDelayMs times 2 to the power of attempt minus 1`() {
        val baseDelay = 1000L
        assertEquals(1000L, BackoffPolicy.EXPONENTIAL.calculateDelay(baseDelay, attempt = 1))
        assertEquals(2000L, BackoffPolicy.EXPONENTIAL.calculateDelay(baseDelay, attempt = 2))
        assertEquals(4000L, BackoffPolicy.EXPONENTIAL.calculateDelay(baseDelay, attempt = 3))
        assertEquals(8000L, BackoffPolicy.EXPONENTIAL.calculateDelay(baseDelay, attempt = 4))
    }

    @Test
    fun `coordinator computes scheduledAt using backoff formula on retry`() = runTest {
        val req = OneTimeTaskRequest.Builder(DummyWorker::class)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, initialDelay = 2.seconds, maxRetries = 3)
            .build()
        h.executor.script = { TaskResult.Retry }
        h.enqueue(req)
        h.runPendingTasks()

        val entity = h.db.taskDao().getTask(req.id)
        assertEquals(TaskState.RETRYING, entity?.state)
        assertEquals(h.clock.nowMs + 2000L, entity?.scheduledAt)
    }
}
