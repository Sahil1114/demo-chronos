package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.TaskState
import io.github.sahilmakkar.chronos.task.policy.UniqueTaskPolicy
import io.github.sahilmakkar.chronos.task.request.PeriodicTaskRequest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class UniqueTaskPolicyTest {

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
    fun `keep policy ignores new task while existing is running or enqueued`() = runTest {
        val req1 = h.request(initialDelay = 1.hours)
        val req2 = h.request()

        h.enqueueUniqueTask("unique_work", UniqueTaskPolicy.KEEP, req1)
        h.runPendingTasks()

        h.enqueueUniqueTask("unique_work", UniqueTaskPolicy.KEEP, req2)
        h.runPendingTasks()

        assertEquals(TaskState.ENQUEUED, h.db.taskDao().getTask(req1.id)?.state)
        assertEquals(null, h.db.taskDao().getTask(req2.id))
    }

    @Test
    fun `keep policy ignores new task after existing finished`() = runTest {
        val req1 = h.request()
        val req2 = h.request()

        h.enqueueUniqueTask("unique_work", UniqueTaskPolicy.KEEP, req1)
        h.runPendingTasks()

        assertEquals(TaskState.SUCCEEDED, h.db.taskDao().getTask(req1.id)?.state)

        h.enqueueUniqueTask("unique_work", UniqueTaskPolicy.KEEP, req2)
        h.runPendingTasks()

        assertEquals(null, h.db.taskDao().getTask(req2.id))
    }

    @Test
    fun `replace policy cancels existing task and runs new one`() = runTest {
        val req1 = h.request(initialDelay = 1.hours)
        val req2 = h.request()

        h.enqueueUniqueTask("unique_work", UniqueTaskPolicy.REPLACE, req1)
        h.runPendingTasks()

        h.enqueueUniqueTask("unique_work", UniqueTaskPolicy.REPLACE, req2)
        h.runPendingTasks()

        assertEquals(TaskState.CANCELLED, h.db.taskDao().getTask(req1.id)?.state)
        assertEquals(TaskState.SUCCEEDED, h.db.taskDao().getTask(req2.id)?.state)
        assertEquals(listOf(req2.id), h.executor.executed)
    }

    @Test
    fun `append onto running or enqueued parent executes parent then child`() = runTest {
        val parent = h.request(initialDelay = 1.hours)
        val child = h.request()

        h.enqueueUniqueTask("append_work", UniqueTaskPolicy.KEEP, parent)
        h.enqueueUniqueTask("append_work", UniqueTaskPolicy.APPEND, child)
        h.runPendingTasks()

        assertEquals(emptyList(), h.executor.executed)

        h.clock.advance(1.hours.inWholeMilliseconds)
        h.runPendingTasks()

        assertEquals(listOf(parent.id, child.id), h.executor.executed)
    }

    @Test
    fun `append onto succeeded parent runs child immediately`() = runTest {
        val parent = h.request()
        val child = h.request()

        h.enqueueUniqueTask("append_work", UniqueTaskPolicy.KEEP, parent)
        h.runPendingTasks()

        assertEquals(listOf(parent.id), h.executor.executed)
        assertEquals(TaskState.SUCCEEDED, h.db.taskDao().getTask(parent.id)?.state)

        h.enqueueUniqueTask("append_work", UniqueTaskPolicy.APPEND, child)
        h.runPendingTasks()

        assertEquals(listOf(parent.id, child.id), h.executor.executed)
    }

    @Test
    fun `append onto cancelled parent cancels child`() = runTest {
        val parent = h.request(initialDelay = 1.hours)
        val child = h.request()

        h.enqueueUniqueTask("append_work", UniqueTaskPolicy.KEEP, parent)
        h.cancelTaskById(parent.id)
        assertEquals(TaskState.CANCELLED, h.db.taskDao().getTask(parent.id)?.state)

        h.enqueueUniqueTask("append_work", UniqueTaskPolicy.APPEND, child)
        h.runPendingTasks()

        assertEquals(TaskState.CANCELLED, h.db.taskDao().getTask(child.id)?.state)
        assertEquals(emptyList(), h.executor.executed)
    }

    @Test
    fun `two appends run in order A then B then C`() = runTest {
        val a = h.request()
        val b = h.request()
        val c = h.request()

        h.enqueueUniqueTask("seq_work", UniqueTaskPolicy.KEEP, a)
        h.enqueueUniqueTask("seq_work", UniqueTaskPolicy.APPEND, b)
        h.enqueueUniqueTask("seq_work", UniqueTaskPolicy.APPEND, c)
        h.runPendingTasks()

        assertEquals(listOf(a.id, b.id, c.id), h.executor.executed)
    }

    @Test
    fun `periodic append rejected without crashing the scope`() = runTest {
        val periodicReq = PeriodicTaskRequest.Builder(DummyWorker::class, repeatInterval = 15.minutes).build()

        assertFailsWith<IllegalArgumentException> {
            h.enqueueUniquePeriodicTask(
                "periodic_unique",
                UniqueTaskPolicy.APPEND,
                periodicReq
            )
        }

        val normalReq = h.request()
        h.enqueue(normalReq)
        h.runPendingTasks()

        assertEquals(listOf(normalReq.id), h.executor.executed)
    }
}
