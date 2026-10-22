package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.TaskState
import io.github.sahilmakkar.chronos.task.policy.BackoffPolicy
import io.github.sahilmakkar.chronos.task.policy.DependentTaskPolicy
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChainExecutionTest {

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
    fun `child runs exactly once after parent succeeds`() = runTest {
        val parent = h.request()
        val child = h.request()
        h.enqueueChain(parent, child)
        h.runPendingTasks()
        assertEquals(listOf(parent.id, child.id), h.executor.executed)
    }

    @Test
    fun `three tasks execute in order A B C`() = runTest {
        val a = h.request()
        val b = h.request()
        val c = h.request()
        h.enqueueChain(a, b, c)
        h.runPendingTasks()
        assertEquals(listOf(a.id, b.id, c.id), h.executor.executed)
    }

    @Test
    fun `stop on failure halts chain`() = runTest {
        val parent = h.request()
        val child = h.request()
        h.executor.script = { entity ->
            if (entity.id == parent.id) TaskResult.Failure() else TaskResult.Success()
        }
        h.enqueueChain(parent, child, failurePolicy = DependentTaskPolicy.STOP_ON_FAILURE)
        h.runPendingTasks()

        assertEquals(listOf(parent.id), h.executor.executed)
        val childEntity = h.db.taskDao().getTask(child.id)
        assertEquals(TaskState.FAILED, childEntity?.state)
    }

    @Test
    fun `continue on failure allows child to execute`() = runTest {
        val parent = h.request()
        val child = h.request()
        h.executor.script = { entity ->
            if (entity.id == parent.id) TaskResult.Failure() else TaskResult.Success()
        }
        h.enqueueChain(parent, child, failurePolicy = DependentTaskPolicy.CONTINUE_ON_FAILURE)
        h.runPendingTasks()

        assertEquals(listOf(parent.id, child.id), h.executor.executed)
    }

    @Test
    fun `parent retry delays child until parent succeeds`() = runTest {
        val parent = h.request()
        val child = h.request()

        var parentAttempts = 0
        h.executor.script = { entity ->
            if (entity.id == parent.id) {
                parentAttempts++
                if (parentAttempts == 1) TaskResult.Retry else TaskResult.Success()
            } else {
                TaskResult.Success()
            }
        }

        h.enqueueChain(parent, child)
        h.runPendingTasks()

        assertEquals(listOf(parent.id), h.executor.executed)

        h.clock.advance(60_000L)
        h.runPendingTasks()

        assertEquals(listOf(parent.id, parent.id, child.id), h.executor.executed)
    }

    @Test
    fun `parent cancelled cancels child`() = runTest {
        val parent = h.request(initialDelay = kotlin.time.Duration.parse("1h"))
        val child = h.request()
        h.enqueueChain(parent, child)

        h.cancelTaskById(parent.id)
        h.clock.advance(3600_000L)
        h.runPendingTasks()

        assertEquals(emptyList(), h.executor.executed)
        val childEntity = h.db.taskDao().getTask(child.id)
        assertEquals(TaskState.CANCELLED, childEntity?.state)
    }

    @Test
    fun `a chain insert never runs child before dependencies exist in database`() = runTest {
        val parent = h.request()
        val child = h.request()

        var dependenciesFoundWhenParentRan = false

        h.executor.script = { entity ->
            if (entity.id == parent.id) {
                runBlocking {
                    val childDeps = h.db.taskDao().getDependenciesForChild(child.id)
                    dependenciesFoundWhenParentRan = childDeps.any { it.parentTaskId == parent.id }
                }
            }
            TaskResult.Success()
        }

        h.enqueueChain(parent, child)
        h.runPendingTasks()

        assertTrue(dependenciesFoundWhenParentRan, "Child dependencies must exist before parent runs")
        assertEquals(listOf(parent.id, child.id), h.executor.executed)
    }
}
