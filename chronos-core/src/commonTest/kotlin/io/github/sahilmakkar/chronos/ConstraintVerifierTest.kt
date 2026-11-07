package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.constraints.BaseConstraintVerifier
import io.github.sahilmakkar.chronos.storage.TaskEntity
import io.github.sahilmakkar.chronos.storage.mapper.toEntity
import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.constraints.EnvironmentConstraints
import io.github.sahilmakkar.chronos.task.constraints.TimeWindow
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import io.github.sahilmakkar.chronos.worker.TaskWorker
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class ConstraintVerifierTest {

    private class SimpleFakeClock(var instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    private class TestConstraintVerifier(clock: Clock) : BaseConstraintVerifier(clock = clock) {
        override fun isSatisfied(entity: TaskEntity): Boolean {
            return verifyTimeWindow(entity)
        }
    }

    private class DummyWorker(taskData: TaskData) : TaskWorker(taskData) {
        override suspend fun doWork(): TaskResult = TaskResult.Success()
    }

    private fun createTaskWithWindow(startHour: Int, endHour: Int, timeZone: String = "UTC"): TaskEntity {
        val window = TimeWindow(startHour = startHour, endHour = endHour, timeZone = timeZone)
        val constraints = EnvironmentConstraints(allowedTimeWindow = window)
        return OneTimeTaskRequest.Builder(DummyWorker::class)
            .setConstraints(constraints)
            .build()
            .toEntity(scheduledAt = 0L)
    }

    @Test
    fun `inside a same-day window is satisfied`() {
        val clock = SimpleFakeClock(Instant.parse("2026-11-02T12:00:00Z"))
        val verifier = TestConstraintVerifier(clock)
        val task = createTaskWithWindow(startHour = 9, endHour = 17, timeZone = "UTC")

        assertTrue(verifier.isSatisfied(task))
    }

    @Test
    fun `outside a same-day window is unsatisfied`() {
        val clock = SimpleFakeClock(Instant.parse("2026-11-02T08:00:00Z"))
        val verifier = TestConstraintVerifier(clock)
        val task = createTaskWithWindow(startHour = 9, endHour = 17, timeZone = "UTC")

        assertFalse(verifier.isSatisfied(task))
    }

    @Test
    fun `overnight window at night is satisfied`() {
        val clock = SimpleFakeClock(Instant.parse("2026-11-02T23:00:00Z"))
        val verifier = TestConstraintVerifier(clock)
        val task = createTaskWithWindow(startHour = 22, endHour = 6, timeZone = "UTC")

        assertTrue(verifier.isSatisfied(task))
    }

    @Test
    fun `overnight window at noon is unsatisfied`() {
        val clock = SimpleFakeClock(Instant.parse("2026-11-02T12:00:00Z"))
        val verifier = TestConstraintVerifier(clock)
        val task = createTaskWithWindow(startHour = 22, endHour = 6, timeZone = "UTC")

        assertFalse(verifier.isSatisfied(task))
    }

    @Test
    fun `invalid timezone string falls back to unsatisfied without throwing`() {
        val clock = SimpleFakeClock(Instant.parse("2026-11-02T12:00:00Z"))
        val verifier = TestConstraintVerifier(clock)
        val task = createTaskWithWindow(startHour = 9, endHour = 17, timeZone = "Invalid/Not_A_Timezone_12345")

        assertFalse(verifier.isSatisfied(task))
    }
}
