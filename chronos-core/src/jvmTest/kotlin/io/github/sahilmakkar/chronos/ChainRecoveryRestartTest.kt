package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.engine.TaskCoordinator
import io.github.sahilmakkar.chronos.storage.TaskDependencyEntity
import io.github.sahilmakkar.chronos.storage.mapper.toEntity
import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.TaskState
import io.github.sahilmakkar.chronos.task.policy.DependentTaskPolicy
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import io.github.sahilmakkar.chronos.testing.FakeClock
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChainRecoveryRestartTest {

    @Test
    fun testChainRecoveryAfterRestartFinishesChain() = runTest {
        val dbFile = File.createTempFile("chain_restart_", ".db").apply { deleteOnExit() }
        val clock = FakeClock(10_000L)

        val taskA = OneTimeTaskRequest.Builder(DummyWorker::class).build()
        val taskB = OneTimeTaskRequest.Builder(DummyWorker::class).build()

        // 1. Session 1: Build state directly in DB: task A succeeded, task B enqueued in future waiting on A
        val db1 = createTestDatabase(dbFile)
        val entityA = taskA.toEntity(scheduledAt = 0L).copy(state = TaskState.SUCCEEDED)
        val futureScheduledAt = 999_999_999L
        val entityB = taskB.toEntity(scheduledAt = futureScheduledAt)
        val dependency = TaskDependencyEntity(
            parentTaskId = taskA.id,
            childTaskId = taskB.id,
            chainFailurePolicy = DependentTaskPolicy.STOP_ON_FAILURE
        )

        db1.taskDao().insertTask(entityA)
        db1.taskDao().insertTask(entityB)
        db1.taskDao().insertDependencies(listOf(dependency))

        // Close DB 1 and simulate process death
        db1.close()

        // 2. Session 2: Reopen same database file
        val db2 = createTestDatabase(dbFile)
        val executor2 = FakeTaskExecutor()
        val coordinator2 = TaskCoordinator(
            db = db2,
            taskExecutor = executor2,
            constraintProvider = FakeConstraintVerifier(),
            clock = clock
        )

        // Before sweepChainRecovery, task B is not due yet
        assertEquals(0, coordinator2.runPendingTasks())
        assertTrue(executor2.executed.isEmpty())

        // Run recovery sweep
        coordinator2.sweepChainRecovery()

        // sweepChainRecovery should have updated scheduledAt of task B to clock.now() (10_000L)
        val updatedTaskB = db2.taskDao().getTask(taskB.id)
        assertEquals(10_000L, updatedTaskB?.scheduledAt)

        // Run coordinator in session 2
        coordinator2.runPendingTasks()

        assertEquals(listOf(taskB.id), executor2.executed)
        assertEquals(TaskState.SUCCEEDED, db2.taskDao().getTask(taskB.id)?.state)

        db2.close()
    }
}
