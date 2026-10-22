package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.task.TaskChain
import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.policy.BackoffPolicy
import io.github.sahilmakkar.chronos.task.policy.DependentTaskPolicy
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import io.github.sahilmakkar.chronos.task.request.PeriodicTaskRequest
import io.github.sahilmakkar.chronos.worker.TaskWorker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TaskRequestTest {

    private class SampleWorker(data: TaskData) : TaskWorker(data) {
        override suspend fun doWork(): TaskResult = TaskResult.Success()
    }

    private fun sampleRequest() = OneTimeTaskRequest.Builder(SampleWorker::class).build()

    @Test
    fun testOneTimeTaskRequestDefaults() {
        val req = OneTimeTaskRequest.Builder(SampleWorker::class).build()

        assertTrue(req.id.isNotBlank())
        assertEquals("io.github.sahilmakkar.chronos.TaskRequestTest.SampleWorker", req.workerClassName)
        assertEquals(Duration.ZERO, req.initialDelay)
        assertEquals(3, req.maxRetries)
        assertEquals(BackoffPolicy.EXPONENTIAL, req.backoffPolicy)
        assertEquals(30.seconds, req.backoffDelay)
        assertNull(req.timeout)
        assertTrue(req.tags.isEmpty())
        assertFalse(req.isPeriodic)
        assertNull(req.repeatInterval)
    }

    @Test
    fun testPeriodicTaskRequestIntervalValidation() {
        val valid = PeriodicTaskRequest.Builder(SampleWorker::class, repeatInterval = 15.minutes).build()
        assertTrue(valid.isPeriodic)
        assertEquals(15.minutes, valid.repeatInterval)

        assertFailsWith<IllegalArgumentException> {
            PeriodicTaskRequest.Builder(SampleWorker::class, repeatInterval = Duration.ZERO).build()
        }

        assertFailsWith<IllegalArgumentException> {
            PeriodicTaskRequest.Builder(SampleWorker::class, repeatInterval = (-5).minutes).build()
        }
    }

    @Test
    fun testTagHandling() {
        val req = OneTimeTaskRequest.Builder(SampleWorker::class)
            .addTag("sync")
            .addTag("network")
            .addTag("sync")
            .build()

        assertEquals(setOf("sync", "network"), req.tags)
    }

    @Test
    fun testCustomParameters() {
        val req = OneTimeTaskRequest.Builder(SampleWorker::class)
            .setInitialDelay(10.seconds)
            .setTimeout(2.hours)
            .setBackoffCriteria(BackoffPolicy.LINEAR, initialDelay = 5.seconds, maxRetries = 10)
            .build()

        assertEquals(10.seconds, req.initialDelay)
        assertEquals(2.hours, req.timeout)
        assertEquals(BackoffPolicy.LINEAR, req.backoffPolicy)
        assertEquals(5.seconds, req.backoffDelay)
        assertEquals(10, req.maxRetries)
    }

    @Test
    fun testLinearBackoffAttempts1To5() {
        val initialDelay = 1000L
        val policy = BackoffPolicy.LINEAR

        assertEquals(1000L, policy.calculateDelay(initialDelay, 1))
        assertEquals(2000L, policy.calculateDelay(initialDelay, 2))
        assertEquals(3000L, policy.calculateDelay(initialDelay, 3))
        assertEquals(4000L, policy.calculateDelay(initialDelay, 4))
        assertEquals(5000L, policy.calculateDelay(initialDelay, 5))
    }

    @Test
    fun testExponentialBackoffAttempts1To5() {
        val initialDelay = 1000L
        val policy = BackoffPolicy.EXPONENTIAL

        assertEquals(1000L, policy.calculateDelay(initialDelay, 1))
        assertEquals(2000L, policy.calculateDelay(initialDelay, 2))
        assertEquals(4000L, policy.calculateDelay(initialDelay, 3))
        assertEquals(8000L, policy.calculateDelay(initialDelay, 4))
        assertEquals(16000L, policy.calculateDelay(initialDelay, 5))
    }

    @Test
    fun testOverflowGuardOnLargeAttempts() {
        val initialDelay = 10_000L

        val expLarge = BackoffPolicy.EXPONENTIAL.calculateDelay(initialDelay, 100)
        assertTrue(expLarge > 0L)
        assertEquals(Long.MAX_VALUE, expLarge)

        val linLarge = BackoffPolicy.LINEAR.calculateDelay(Long.MAX_VALUE - 10, 2)
        assertEquals(Long.MAX_VALUE, linLarge)
    }

    @Test
    fun testZeroOrNegativeAttemptReturnsZero() {
        assertEquals(0L, BackoffPolicy.LINEAR.calculateDelay(1000L, 0))
        assertEquals(0L, BackoffPolicy.LINEAR.calculateDelay(1000L, -1))
        assertEquals(0L, BackoffPolicy.EXPONENTIAL.calculateDelay(1000L, 0))
        assertEquals(0L, BackoffPolicy.EXPONENTIAL.calculateDelay(1000L, -1))
    }

    @Test
    fun testThenDoesNotMutateOriginalChain() {
        val r1 = sampleRequest()
        val r2 = sampleRequest()

        val chain1 = TaskChain.start(r1)
        val chain2 = chain1.then(r2, DependentTaskPolicy.STOP_ON_FAILURE)

        assertEquals(1, chain1.requests.size)
        assertEquals(0, chain1.failurePolicies.size)

        assertEquals(2, chain2.requests.size)
        assertEquals(1, chain2.failurePolicies.size)
        assertEquals(DependentTaskPolicy.STOP_ON_FAILURE, chain2.failurePolicies.first())
    }

    @Test
    fun testFailurePoliciesSizeEqualsRequestsMinusOne() {
        val r1 = sampleRequest()
        val r2 = sampleRequest()
        val r3 = sampleRequest()
        val r4 = sampleRequest()

        val chain = TaskChain.start(r1)
            .then(r2, DependentTaskPolicy.STOP_ON_FAILURE)
            .then(r3, DependentTaskPolicy.CONTINUE_ON_FAILURE)
            .then(r4, DependentTaskPolicy.STOP_ON_FAILURE)

        assertEquals(4, chain.requests.size)
        assertEquals(3, chain.failurePolicies.size)
        assertEquals(chain.requests.size - 1, chain.failurePolicies.size)
        assertEquals(
            listOf(
                DependentTaskPolicy.STOP_ON_FAILURE,
                DependentTaskPolicy.CONTINUE_ON_FAILURE,
                DependentTaskPolicy.STOP_ON_FAILURE
            ),
            chain.failurePolicies
        )
    }
}
