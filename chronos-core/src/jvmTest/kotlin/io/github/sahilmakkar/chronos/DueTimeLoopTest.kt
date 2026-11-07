package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.engine.DueTimeLoop
import io.github.sahilmakkar.chronos.storage.mapper.toEntity
import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.TaskState
import io.github.sahilmakkar.chronos.task.policy.BackoffPolicy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class DueTimeLoopTest {

    private lateinit var h: TestHarness

    @BeforeTest
    fun setUp() {
        h = TestHarness(queryCoroutineContext = kotlinx.coroutines.Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() = runBlocking {
        h.close()
    }

    @Test
    fun `task with initial delay runs once fake clock advances without real sleeping`() = runTest {
        val loop = DueTimeLoop(
            db = h.db,
            coordinator = h.coordinator,
            clock = h.clock
        )
        val req = h.request(initialDelay = 10.seconds)
        h.db.taskDao().insertTask(req.toEntity(scheduledAt = h.clock.nowMs + 10_000L))

        val states = listOf(TaskState.ENQUEUED, TaskState.RETRYING)
        h.db.taskDao().observeNextDueAt(states).first { it != null }

        loop.start(backgroundScope)
        runCurrent()

        advanceTimeBy(5_000L)
        assertEquals(emptyList(), h.executor.executed)

        h.clock.advance(10_000L)
        advanceTimeBy(6_000L)
        runCurrent()

        assertEquals(listOf(req.id), h.executor.executed)
        assertEquals(TaskState.SUCCEEDED, h.db.taskDao().getTask(req.id)?.state)
        loop.stopAndJoin()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `retry backoff runs task again after backoff delay and not before`() = runTest {
        val loop = DueTimeLoop(
            db = h.db,
            coordinator = h.coordinator,
            clock = h.clock
        )
        var attempts = 0
        h.executor.script = {
            attempts++
            if (attempts == 1) TaskResult.Retry else TaskResult.Success()
        }

        val req = io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest.Builder(DummyWorker::class)
            .setBackoffCriteria(BackoffPolicy.LINEAR, initialDelay = 5.seconds, maxRetries = 2)
            .build()

        h.db.taskDao().insertTask(req.toEntity(scheduledAt = h.clock.nowMs))
        val states = listOf(TaskState.ENQUEUED, TaskState.RETRYING)
        h.db.taskDao().observeNextDueAt(states).first { it != null }

        loop.start(backgroundScope)
        runCurrent()

        advanceTimeBy(100L.milliseconds)
        assertEquals(1, attempts)
        assertEquals(TaskState.RETRYING, h.db.taskDao().getTask(req.id)?.state)

        h.clock.advance(2_000L)
        advanceTimeBy(2_000L.milliseconds)
        assertEquals(1, attempts)

        h.clock.advance(3_500L)
        advanceTimeBy(3_500L.milliseconds)
        runCurrent()

        assertEquals(2, attempts)
        assertEquals(TaskState.SUCCEEDED, h.db.taskDao().getTask(req.id)?.state)
        loop.stopAndJoin()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `blocked task does not cause more than one coordinator pass per blockedRecheckInterval`() = runTest {
        h.constraintVerifier.satisfied = false
        val loop = DueTimeLoop(
            db = h.db,
            coordinator = h.coordinator,
            clock = h.clock,
            blockedRecheckInterval = 15.seconds
        )
        val req = h.request()
        h.db.taskDao().insertTask(req.toEntity(scheduledAt = h.clock.nowMs))
        val states = listOf(TaskState.ENQUEUED, TaskState.RETRYING)
        h.db.taskDao().observeNextDueAt(states).first { it != null }

        loop.start(backgroundScope)
        runCurrent()

        advanceTimeBy(100L.milliseconds)
        assertEquals(1, h.constraintVerifier.checkCount)

        h.clock.advance(14_000L)
        advanceTimeBy(14_000L.milliseconds)
        assertEquals(1, h.constraintVerifier.checkCount)

        h.clock.advance(2_000L)
        advanceTimeBy(2_000L.milliseconds)
        runCurrent()

        assertEquals(2, h.constraintVerifier.checkCount)
        loop.stopAndJoin()
    }
}
