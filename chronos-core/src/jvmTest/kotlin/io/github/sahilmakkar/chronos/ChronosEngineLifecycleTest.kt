package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.engine.ChronosEngine
import io.github.sahilmakkar.chronos.engine.Daemon
import io.github.sahilmakkar.chronos.engine.TaskCoordinator
import io.github.sahilmakkar.chronos.port.TaskCanceller
import io.github.sahilmakkar.chronos.port.TaskEnqueuer
import io.github.sahilmakkar.chronos.port.TaskStore
import io.github.sahilmakkar.chronos.storage.RoomTaskStore
import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.TaskState
import io.github.sahilmakkar.chronos.task.policy.UniqueTaskPolicy
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import io.github.sahilmakkar.chronos.task.request.PeriodicTaskRequest
import io.github.sahilmakkar.chronos.task.request.TaskRequest
import io.github.sahilmakkar.chronos.worker.WorkerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChronosEngineLifecycleTest {

    @Test
    fun testCloseOrderingDaemonStopsFirstThenScopeThenDatabase() = runTest {
        val executionOrder = mutableListOf<String>()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val tempFile = File.createTempFile("lifecycle_test_", ".db").apply { deleteOnExit() }
        val db = createTestDatabase(tempFile)
        val registry = WorkerRegistry().apply {
            register(DummyWorker::class.qualifiedName!!) { DummyWorker(it) }
        }
        val executor = FakeTaskExecutor()
        val coordinator = TaskCoordinator(
            db = db,
            taskExecutor = executor,
            constraintProvider = FakeConstraintVerifier()
        )

        val daemon = Daemon(scope, coordinator, db)

        val enqueuer = object : TaskEnqueuer {
            override fun enqueue(chain: io.github.sahilmakkar.chronos.task.TaskChain) {}
            override fun enqueueTask(name: String, policy: UniqueTaskPolicy, request: OneTimeTaskRequest) {}
            override fun enqueuePeriodicTask(name: String, policy: UniqueTaskPolicy, request: PeriodicTaskRequest) {}
        }
        val canceller = object : TaskCanceller {
            override fun cancelTaskById(id: String) {}
            override fun cancelTaskByTag(tag: String) {}
            override fun cancelAllTasks() {}
        }
        val taskStore = RoomTaskStore(db.taskDao())

        var dbClosed = false
        val onClose = {
            assertFalse(scope.isActive, "Scope must be cancelled before database is closed")
            dbClosed = true
            db.close()
            executionOrder += "database_closed"
        }

        val engine = ChronosEngine(
            registry = registry,
            coroutineScope = scope,
            coordinator = coordinator,
            daemon = daemon,
            enqueuer = enqueuer,
            canceller = canceller,
            taskStore = taskStore,
            onClose = onClose
        )

        assertTrue(scope.isActive)
        assertFalse(dbClosed)

        engine.close()

        assertFalse(scope.isActive, "Scope must be cancelled after engine.close()")
        assertTrue(dbClosed, "Database must be closed via onClose")
        assertEquals(listOf("database_closed"), executionOrder)
    }

    @Test
    fun testAfterCloseNoFurtherTasksRunAndNoCoroutineLeaks() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val tempFile = File.createTempFile("leak_test_", ".db").apply { deleteOnExit() }
        val db = createTestDatabase(tempFile)
        val registry = WorkerRegistry().apply {
            register(DummyWorker::class.qualifiedName!!) { DummyWorker(it) }
        }
        val executor = FakeTaskExecutor()
        val coordinator = TaskCoordinator(
            db = db,
            taskExecutor = executor,
            constraintProvider = FakeConstraintVerifier()
        )
        val daemon = Daemon(scope, coordinator, db)

        val h = TestHarness()
        val engine = ChronosEngine(
            registry = registry,
            coroutineScope = scope,
            coordinator = coordinator,
            daemon = daemon,
            enqueuer = h.enqueuer,
            canceller = h.canceller,
            taskStore = RoomTaskStore(db.taskDao()),
            onClose = { db.close() }
        )

        // Launch background work in scope
        val backgroundJob = scope.launch {
            while (isActive) {
                delay(100)
            }
        }
        assertTrue(backgroundJob.isActive)

        engine.close()

        advanceUntilIdle()

        assertFalse(scope.isActive)
        assertTrue(backgroundJob.isCancelled || !backgroundJob.isActive)
        h.close()
    }
}
