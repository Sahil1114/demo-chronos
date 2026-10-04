package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.TaskState
import io.github.sahilmakkar.chronos.task.constraints.EnvironmentConstraints
import io.github.sahilmakkar.chronos.task.constraints.TimeWindow
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.TaskInfo
import io.github.sahilmakkar.chronos.task.model.workDataOf
import io.github.sahilmakkar.chronos.task.policy.BackoffPolicy
import io.github.sahilmakkar.chronos.task.policy.DependentTaskPolicy
import io.github.sahilmakkar.chronos.task.policy.UniqueTaskPolicy
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import io.github.sahilmakkar.chronos.task.request.PeriodicTaskRequest
import io.github.sahilmakkar.chronos.worker.Worker
import io.github.sahilmakkar.chronos.worker.WorkerRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class JvmChronosTest {

    private lateinit var registry: WorkerRegistry
    private lateinit var chronos: Chronos

    class SuccessWorker(data: TaskData) : Worker(inputData = data) {
        override suspend fun doWork(): TaskResult =
            TaskResult.Success(outputData = workDataOf("ok" to true))
    }

    class FailureWorker(data: TaskData) : Worker(inputData = data) {
        override suspend fun doWork(): TaskResult =
            TaskResult.Failure(outputData = workDataOf("failed" to true))
    }

    class RetryWorker(data: TaskData) : Worker(inputData = data) {
        override suspend fun doWork(): TaskResult = TaskResult.Retry
    }

    class TimeoutWorker(data: TaskData) : Worker(inputData = data) {
        override suspend fun doWork(): TaskResult {
            delay(2000.milliseconds)
            return TaskResult.Success()
        }
    }

    @BeforeTest
    fun setUp() {
        registry = WorkerRegistry().apply {
            register(className = "io.github.sahilmakkar.chronos.JvmChronosTest.SuccessWorker") {
                SuccessWorker(
                    data = it
                )
            }
            register(className = "io.github.sahilmakkar.chronos.JvmChronosTest.FailureWorker") {
                FailureWorker(
                    data = it
                )
            }
            register(className = "io.github.sahilmakkar.chronos.JvmChronosTest.RetryWorker") {
                RetryWorker(
                    data = it
                )
            }
            this.register("io.github.sahilmakkar.chronos.JvmChronosTest.TimeoutWorker") {
                TimeoutWorker(
                    data = it
                )
            }
        }
        chronos = Chronos.initialize(support = PlatformSupport(), registry = registry)
    }

    @AfterTest
    fun tearDown() {
        Chronos.shutdown()
    }

    private suspend fun awaitState(id: String, state: TaskState, timeoutMs: Long = 5000): TaskInfo {
        val info = withTimeoutOrNull(timeoutMs.milliseconds) {
            chronos.getWorkInfoFlow(id).first { it?.state == state }
        }
        if (info == null) {
            val current = chronos.getWorkInfoFlow(id).first()
            fail("Timed out waiting for task $id to reach state $state. Current state: ${current?.state}")
        }
        return info
    }

    @Test
    fun testUniqueWorkPolicyKeep() = runBlocking {
        val req1 = OneTimeTaskRequest.Builder(workerClass = SuccessWorker::class).build()
        val req2 = OneTimeTaskRequest.Builder(workerClass = SuccessWorker::class).build()

        chronos.enqueueUniqueWork(
            name = "unique_job",
            policy = UniqueTaskPolicy.KEEP,
            request = req1
        )
        awaitState(id = req1.id, state = TaskState.SUCCEEDED)

        chronos.enqueueUniqueWork(
            name = "unique_job",
            policy = UniqueTaskPolicy.KEEP,
            request = req2
        )
        delay(timeMillis = 200)

        val info2 = chronos.getWorkInfoFlow(req2.id).first()
        assertNull(actual = info2)
    }

    @Test
    fun testUniqueWorkPolicyReplace(): Unit = runBlocking {
        val req1 = OneTimeTaskRequest.Builder(workerClass = SuccessWorker::class)
            .setInitialDelay(10.minutes).build()
        val req2 = OneTimeTaskRequest.Builder(workerClass = SuccessWorker::class).build()

        chronos.enqueueUniqueWork(
            name = "replace_job",
            policy = UniqueTaskPolicy.KEEP,
            request = req1
        )
        awaitState(id = req1.id, state = TaskState.ENQUEUED)

        chronos.enqueueUniqueWork(
            name = "replace_job",
            policy = UniqueTaskPolicy.REPLACE,
            request = req2
        )
        awaitState(id = req1.id, state = TaskState.CANCELLED)
        awaitState(id = req2.id, state = TaskState.SUCCEEDED)
    }

    @Test
    fun testUniqueWorkPolicyAppend(): Unit = runBlocking {
        val req1 = OneTimeTaskRequest.Builder(workerClass = SuccessWorker::class).build()
        val req2 = OneTimeTaskRequest.Builder(workerClass = SuccessWorker::class).build()

        chronos.enqueueUniqueWork(
            name = "append_job",
            policy = UniqueTaskPolicy.KEEP,
            request = req1
        )
        awaitState(id = req1.id, state = TaskState.SUCCEEDED)

        chronos.enqueueUniqueWork(
            name = "append_job",
            policy = UniqueTaskPolicy.APPEND,
            request = req2
        )
        awaitState(id = req2.id, state = TaskState.SUCCEEDED)
    }

    @Test
    fun testWorkChainStopOnFailure(): Unit = runBlocking {
        val req1 = OneTimeTaskRequest.Builder(FailureWorker::class).build()
        val req2 = OneTimeTaskRequest.Builder(SuccessWorker::class).build()

        val chain = chronos.beginWith(request = req1).then(
            request = req2,
            onFailure = DependentTaskPolicy.STOP_ON_FAILURE
        )
        chronos.enqueue(chain)

        awaitState(req1.id, TaskState.FAILED)
        awaitState(req2.id, TaskState.FAILED)
    }

    @Test
    fun testWorkChainContinueOnFailure(): Unit = runBlocking {
        val req1 = OneTimeTaskRequest.Builder(FailureWorker::class).build()
        val req2 = OneTimeTaskRequest.Builder(SuccessWorker::class).build()

        val chain = chronos.beginWith(request = req1).then(
            request = req2,
            onFailure = DependentTaskPolicy.CONTINUE_ON_FAILURE
        )
        chronos.enqueue(chain)

        awaitState(req1.id, TaskState.FAILED)
        awaitState(req2.id, TaskState.SUCCEEDED)
    }

    @Test
    fun testRetryBackoffAndCap(): Unit = runBlocking {
        val req = OneTimeTaskRequest.Builder(workerClass = RetryWorker::class)
            .setBackoffCriteria(
                policy = BackoffPolicy.LINEAR,
                initialDelay = 1.seconds,
                maxRetries = 2
            )
            .build()

        chronos.enqueue(req)
        awaitState(req.id, TaskState.RETRYING)

        awaitState(req.id, TaskState.FAILED)
    }

    @Test
    fun testTimeoutCancellation(): Unit = runBlocking {
        val req = OneTimeTaskRequest.Builder(TimeoutWorker::class)
            .setTimeout(1.seconds)
            .build()

        chronos.enqueue(req)
        awaitState(req.id, TaskState.FAILED)
    }

    @Test
    fun testTagBasedCancellation(): Unit = runBlocking {
        val req1 = OneTimeTaskRequest.Builder(SuccessWorker::class).setInitialDelay(10.minutes)
            .addTag("tag-x").build()
        val req2 = OneTimeTaskRequest.Builder(SuccessWorker::class).setInitialDelay(10.minutes)
            .addTag("tag-x").build()

        chronos.enqueue(req1)
        chronos.enqueue(req2)
        awaitState(req1.id, TaskState.ENQUEUED)
        awaitState(req2.id, TaskState.ENQUEUED)

        chronos.cancelWorkByTag("tag-x")
        awaitState(req1.id, TaskState.CANCELLED)
        awaitState(req2.id, TaskState.CANCELLED)
    }

    @Test
    fun testTimeWindowConstraint(): Unit = runBlocking {
        val req = OneTimeTaskRequest.Builder(SuccessWorker::class)
            .setConstraints(
                EnvironmentConstraints.Builder()
                    .setAllowedTimeWindow(TimeWindow(startHour = 2, endHour = 5, timeZone = "UTC"))
                    .build()
            )
            .build()

        chronos.enqueue(req)
        awaitState(req.id, TaskState.ENQUEUED)

        awaitState(req.id, TaskState.SUCCEEDED)
    }

    @Test
    fun testPeriodicTaskTriggers(): Unit = runBlocking {
        val req = PeriodicTaskRequest.Builder(SuccessWorker::class, 1.hours).build()

        chronos.enqueue(req)
        awaitState(req.id, TaskState.ENQUEUED)

        val info = chronos.getWorkInfoFlow(req.id).first()
        assertNotNull(info)
        assertEquals(0, info.runAttemptCount)

        delay(1500.milliseconds)
        awaitState(req.id, TaskState.ENQUEUED)
    }
}