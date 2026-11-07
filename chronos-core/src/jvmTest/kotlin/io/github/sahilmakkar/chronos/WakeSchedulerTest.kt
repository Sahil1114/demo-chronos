package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.testing.FakeClock
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class WakeSchedulerTest {

    private lateinit var fakeClock: FakeClock
    private lateinit var fakeWakeScheduler: FakeWakeScheduler
    private lateinit var h: TestHarness

    @BeforeTest
    fun setUp() {
        fakeClock = FakeClock(1000L)
        h = TestHarness(
            clock = fakeClock,
            wakeSchedulerFactory = { db ->
                FakeWakeScheduler(db = db, clock = fakeClock).also { fakeWakeScheduler = it }
            }
        )
    }

    @AfterTest
    fun tearDown() = runTest {
        h.close()
    }

    @Test
    fun `enqueueing task schedules wake request with correct delay and cancelling cancels it`() = runTest {
        val req30 = h.request(initialDelay = 30.seconds)
        h.enqueue(req30)

        assertEquals(30_000L, fakeWakeScheduler.scheduledDelayMs)
        assertEquals(false, fakeWakeScheduler.cancelled)

        h.cancelTaskById(req30.id)

        assertEquals(null, fakeWakeScheduler.scheduledDelayMs)
        assertTrue(fakeWakeScheduler.cancelled)
    }

    @Test
    fun `cancelling earlier task reschedules wake request to subsequent task due time`() = runTest {
        val req30 = h.request(initialDelay = 30.seconds)
        val req60 = h.request(initialDelay = 60.seconds)
        h.enqueue(req30)
        h.enqueue(req60)

        assertEquals(30_000L, fakeWakeScheduler.scheduledDelayMs)

        h.cancelTaskById(req30.id)

        assertEquals(60_000L, fakeWakeScheduler.scheduledDelayMs)
        assertEquals(false, fakeWakeScheduler.cancelled)
    }
}
