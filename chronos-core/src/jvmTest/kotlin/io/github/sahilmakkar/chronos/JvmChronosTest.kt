package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.TaskState
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.TaskInfo
import io.github.sahilmakkar.chronos.task.model.taskDataOf
import io.github.sahilmakkar.chronos.task.policy.UniqueTaskPolicy
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import io.github.sahilmakkar.chronos.worker.TaskWorker
import io.github.sahilmakkar.chronos.worker.WorkerRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class JvmChronosTest {

    private lateinit var registry: WorkerRegistry
    private lateinit var chronos: Chronos

    class SuccessWorker(data: TaskData) : TaskWorker(inputData = data) {
        override suspend fun doWork(): TaskResult =
            TaskResult.Success(outputData = taskDataOf("ok" to true))
    }

    @BeforeTest
    fun setUp() {
        cleanDbFiles()
        registry = WorkerRegistry().apply {
            register(className = "io.github.sahilmakkar.chronos.JvmChronosTest.SuccessWorker") {
                SuccessWorker(data = it)
            }
        }
        chronos = Chronos.initialize(support = PlatformSupport(), registry = registry)
    }

    @AfterTest
    fun tearDown() {
        Chronos.shutdown()
        cleanDbFiles()
    }

    private fun cleanDbFiles() {
        val home = System.getProperty("user.home")
        File(home, ".chronos/chronos_work.db").delete()
        File(home, ".chronos/chronos_work.db-wal").delete()
        File(home, ".chronos/chronos_work.db-shm").delete()
    }

    private suspend fun awaitState(id: String, state: TaskState, timeoutMs: Long = 5000): TaskInfo {
        val info = withTimeoutOrNull(timeoutMs.milliseconds) {
            chronos.observeTask(id).first { it?.state == state }
        }
        if (info == null) {
            val current = chronos.observeTask(id).first()
            fail("Timed out waiting for task $id to reach state $state. Current state: ${current?.state}")
        }
        return info
    }

    @Test
    fun testOneTimeTaskSuccess(): Unit = runBlocking {
        val req = OneTimeTaskRequest.Builder(workerClass = SuccessWorker::class).build()
        chronos.enqueue(req)

        val info = awaitState(req.id, TaskState.SUCCEEDED)
        assertNotNull(info)
        assertEquals(true, info.outputData.getBoolean("ok", false))
    }

    @Test
    fun testUniqueWorkPolicyKeep(): Unit = runBlocking {
        val req1 = OneTimeTaskRequest.Builder(workerClass = SuccessWorker::class).build()
        val req2 = OneTimeTaskRequest.Builder(workerClass = SuccessWorker::class).build()

        chronos.enqueueUniqueTask(
            name = "unique_job",
            policy = UniqueTaskPolicy.KEEP,
            request = req1
        )
        awaitState(id = req1.id, state = TaskState.SUCCEEDED)

        chronos.enqueueUniqueTask(
            name = "unique_job",
            policy = UniqueTaskPolicy.KEEP,
            request = req2
        )

        val info2 = chronos.observeTask(req2.id).first()
        assertNull(actual = info2)
    }

    @Test
    fun testWorkChainExecution(): Unit = runBlocking {
        val req1 = OneTimeTaskRequest.Builder(SuccessWorker::class).build()
        val req2 = OneTimeTaskRequest.Builder(SuccessWorker::class).build()

        val chain = chronos.beginWith(req1).then(req2)
        chronos.enqueue(chain)

        awaitState(req1.id, TaskState.SUCCEEDED)
        awaitState(req2.id, TaskState.SUCCEEDED)
    }

    @Test
    fun testTagBasedCancellation(): Unit = runBlocking {
        val req = OneTimeTaskRequest.Builder(SuccessWorker::class)
            .setInitialDelay(10.minutes)
            .addTag("smoke-tag")
            .build()

        chronos.enqueue(req)
        awaitState(req.id, TaskState.ENQUEUED)

        chronos.cancelTaskByTag("smoke-tag")
        awaitState(req.id, TaskState.CANCELLED)
    }
}
