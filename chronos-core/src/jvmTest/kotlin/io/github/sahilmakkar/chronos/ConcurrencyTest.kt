package io.github.sahilmakkar.chronos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ConcurrencyTest {

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
    fun `100 tasks enqueued while poller runs and each executes exactly once`() = runTest(timeout = 30.seconds) {
        val totalTasks = 100
        val requests = (1..totalTasks).map { h.request() }
        val requestIds = requests.map { it.id }.toSet()

        var pollerActive = true
        val pollerJob = launch(Dispatchers.Default) {
            while (pollerActive && isActive) {
                h.coordinator.runPendingTasks()
                delay(5.milliseconds)
            }
        }

        val enqueueJobs = requests.map { req ->
            launch(Dispatchers.Default) {
                h.enqueuer.enqueue(req)
            }
        }
        enqueueJobs.forEach { it.join() }

        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            val count = synchronized(h.executor.executed) { h.executor.executed.size }
            if (count >= totalTasks) break
            delay(10.milliseconds)
        }

        pollerActive = false
        pollerJob.cancel()
        h.scope.coroutineContext[Job]?.children?.toList()?.forEach { it.join() }
        h.runPendingTasks()

        val executedIds = synchronized(h.executor.executed) { h.executor.executed.toList() }
        assertEquals(totalTasks, executedIds.size, "Total executed task count must equal 100")
        assertEquals(totalTasks, executedIds.toSet().size, "Every task must be executed exactly once with no duplicates")
        assertEquals(requestIds, executedIds.toSet(), "All enqueued task IDs must be executed")
    }
}
