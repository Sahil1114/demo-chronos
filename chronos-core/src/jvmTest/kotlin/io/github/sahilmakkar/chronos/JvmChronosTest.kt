package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.testing.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.Instant
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.sahilmakkar.chronos.task.policy.BackoffPolicy
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import io.github.sahilmakkar.chronos.task.request.PeriodicTaskRequest
import io.github.sahilmakkar.chronos.task.constraints.TimeWindow
import io.github.sahilmakkar.chronos.task.constraints.EnvironmentConstraints
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.workDataOf
import io.github.sahilmakkar.chronos.worker.Worker
import io.github.sahilmakkar.chronos.worker.WorkerRegistry
import io.github.sahilmakkar.chronos.roomDb.WorkDatabase
import io.github.sahilmakkar.chronos.task.policy.UniqueTaskPolicy
import io.github.sahilmakkar.chronos.task.policy.DependentTaskPolicy
import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.TaskState

class JvmChronosTest {

    private lateinit var registry: WorkerRegistry
    private lateinit var clock: FakeClock
    private lateinit var chronos: Chronos
    private lateinit var db: WorkDatabase

    class SuccessWorker(data: TaskData) : Worker(data) {
        override suspend fun doWork(): TaskResult = TaskResult.Success(workDataOf("ok" to true))
    }

    class FailureWorker(data: TaskData) : Worker(data) {
        override suspend fun doWork(): TaskResult = TaskResult.Failure(workDataOf("failed" to true))
    }

    class RetryWorker(data: TaskData) : Worker(data) {
        override suspend fun doWork(): TaskResult = TaskResult.Retry
    }

    class TimeoutWorker(data: TaskData) : Worker(data) {
        override suspend fun doWork(): TaskResult {
            delay(2000)
            return TaskResult.Success()
        }
    }

    @BeforeTest
    fun setUp() {
        registry = WorkerRegistry().apply {
            register("io.github.sahilmakkar.chronos.JvmChronosTest.SuccessWorker") { SuccessWorker(it) }
            register("io.github.sahilmakkar.chronos.JvmChronosTest.FailureWorker") { FailureWorker(it) }
            register("io.github.sahilmakkar.chronos.JvmChronosTest.RetryWorker") { RetryWorker(it) }
            register("io.github.sahilmakkar.chronos.JvmChronosTest.TimeoutWorker") { TimeoutWorker(it) }
        }
        clock = FakeClock(Instant.fromEpochMilliseconds(1000000))
        db = Room.inMemoryDatabaseBuilder<WorkDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        Chronos.customDatabase = db
        chronos = Chronos(registry, clock)
    }

    @AfterTest
    fun tearDown() {
        chronos.close()
        Chronos.customDatabase = null
    }

    private suspend fun awaitState(id: String, state: TaskState, timeoutMs: Long = 5000) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val all = db.workDao().getAllWorkRequests()
            val current = all.find { it.id == id }
            if (current?.state == state.name) {
                return
            }
            delay(50)
        }
        val all = db.workDao().getAllWorkRequests()
        val current = all.find { it.id == id }
        println("Timeout waiting for $id to reach $state. Database contents:")
        all.forEach { println(" - ID: ${it.id}, Class: ${it.workerClassName}, State: ${it.state}, ScheduledAt: ${it.scheduledAt}, runAttemptCount: ${it.runAttemptCount}") }
        fail("Timed out waiting for task $id to reach state $state. Current state: ${current?.state}")
    }

    @Test
    fun testUniqueWorkPolicyKeep() = runBlocking {
        val req1 = OneTimeTaskRequest.Builder(SuccessWorker::class).build()
        val req2 = OneTimeTaskRequest.Builder(SuccessWorker::class).build()

        chronos.enqueueUniqueWork("unique_job", UniqueTaskPolicy.KEEP, req1)
        awaitState(req1.id, TaskState.SUCCEEDED)

        chronos.enqueueUniqueWork("unique_job", UniqueTaskPolicy.KEEP, req2)
        delay(200)

        val info2 = chronos.getWorkInfoFlow(req2.id).first()
        assertNull(info2) // Ignored
    }

    @Test
    fun testUniqueWorkPolicyReplace() = runBlocking {
        val req1 = OneTimeTaskRequest.Builder(SuccessWorker::class).setInitialDelay(10.minutes).build()
        val req2 = OneTimeTaskRequest.Builder(SuccessWorker::class).build()

        chronos.enqueueUniqueWork("replace_job", UniqueTaskPolicy.KEEP, req1)
        awaitState(req1.id, TaskState.ENQUEUED)

        chronos.enqueueUniqueWork("replace_job", UniqueTaskPolicy.REPLACE, req2)
        awaitState(req1.id, TaskState.CANCELLED)
        awaitState(req2.id, TaskState.SUCCEEDED)
    }

    @Test
    fun testUniqueWorkPolicyAppend() = runBlocking {
        val req1 = OneTimeTaskRequest.Builder(SuccessWorker::class).build()
        val req2 = OneTimeTaskRequest.Builder(SuccessWorker::class).build()

        chronos.enqueueUniqueWork("append_job", UniqueTaskPolicy.KEEP, req1)
        awaitState(req1.id, TaskState.SUCCEEDED)

        chronos.enqueueUniqueWork("append_job", UniqueTaskPolicy.APPEND, req2)
        awaitState(req2.id, TaskState.SUCCEEDED)
    }

    @Test
    fun testWorkChainStopOnFailure() = runBlocking {
        val req1 = OneTimeTaskRequest.Builder(FailureWorker::class).build()
        val req2 = OneTimeTaskRequest.Builder(SuccessWorker::class).build()

        val chain = chronos.beginWith(req1).then(req2, DependentTaskPolicy.STOP_ON_FAILURE)
        chronos.enqueue(chain)

        awaitState(req1.id, TaskState.FAILED)
        awaitState(req2.id, TaskState.FAILED)
    }

    @Test
    fun testWorkChainContinueOnFailure() = runBlocking {
        val req1 = OneTimeTaskRequest.Builder(FailureWorker::class).build()
        val req2 = OneTimeTaskRequest.Builder(SuccessWorker::class).build()

        val chain = chronos.beginWith(req1).then(req2, DependentTaskPolicy.CONTINUE_ON_FAILURE)
        chronos.enqueue(chain)

        awaitState(req1.id, TaskState.FAILED)
        awaitState(req2.id, TaskState.SUCCEEDED)
    }

    @Test
    fun testRetryBackoffAndCap() = runBlocking {
        val req = OneTimeTaskRequest.Builder(RetryWorker::class)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 1.seconds, 2)
            .build()

        chronos.enqueue(req)
        awaitState(req.id, TaskState.RETRYING)

        // Advance to trigger retry
        clock.advanceBy(2.seconds)
        awaitState(req.id, TaskState.FAILED) // Hits cap (attempt 2)
    }

    @Test
    fun testTimeoutCancellation() = runBlocking {
        val req = OneTimeTaskRequest.Builder(TimeoutWorker::class)
            .setTimeout(1.seconds)
            .build()

        chronos.enqueue(req)
        awaitState(req.id, TaskState.FAILED)
    }

    @Test
    fun testTagBasedCancellation() = runBlocking {
        val req1 = OneTimeTaskRequest.Builder(SuccessWorker::class).setInitialDelay(10.minutes).addTag("tag-x").build()
        val req2 = OneTimeTaskRequest.Builder(SuccessWorker::class).setInitialDelay(10.minutes).addTag("tag-x").build()

        chronos.enqueue(req1)
        chronos.enqueue(req2)
        awaitState(req1.id, TaskState.ENQUEUED)
        awaitState(req2.id, TaskState.ENQUEUED)

        chronos.cancelWorkByTag("tag-x")
        awaitState(req1.id, TaskState.CANCELLED)
        awaitState(req2.id, TaskState.CANCELLED)
    }

    @Test
    fun testTimeWindowConstraint() = runBlocking {
        val req = OneTimeTaskRequest.Builder(SuccessWorker::class)
            .setConstraints(
                EnvironmentConstraints.Builder()
                    .setAllowedTimeWindow(TimeWindow(startHour = 2, endHour = 5, timeZone = "UTC"))
                    .build()
            )
            .build()

        chronos.enqueue(req)
        awaitState(req.id, TaskState.ENQUEUED) // Blocked

        clock.advanceBy(2.hours)
        awaitState(req.id, TaskState.SUCCEEDED) // Runs inside window
    }

    @Test
    fun testPeriodicTaskTriggers() = runBlocking {
        val req = PeriodicTaskRequest.Builder(SuccessWorker::class, 1.hours).build()

        chronos.enqueue(req)
        // Should run once and reschedule
        awaitState(req.id, TaskState.ENQUEUED)

        var info = chronos.getWorkInfoFlow(req.id).first()
        assertNotNull(info)
        assertEquals(0, info.runAttemptCount) // Resets on success

        clock.advanceBy(1.hours)
        // Should run second time and reschedule again
        delay(1500)
        awaitState(req.id, TaskState.ENQUEUED)
    }
}
