package io.github.sahilmakkar.chronos.testing

import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.TaskState
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.policy.UniqueTaskPolicy
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import io.github.sahilmakkar.chronos.worker.TaskWorker
import io.github.sahilmakkar.chronos.worker.WorkerRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TestChronosTest {

    private class SampleWorker(data: TaskData) : TaskWorker(data) {
        override suspend fun doWork(): TaskResult = TaskResult.Success()
    }

    private val registry = WorkerRegistry().apply {
        register("io.github.sahilmakkar.chronos.testing.TestChronosTest.SampleWorker") { SampleWorker(it) }
    }

    @Test
    fun testCancelTaskByIdSetsState() = runTest {
        val testChronos = TestChronos(registry)
        val req = OneTimeTaskRequest.Builder(SampleWorker::class).build()

        testChronos.enqueueTask("cancel_by_id", UniqueTaskPolicy.REPLACE, req)
        val initial = testChronos.observeTask(req.id).first()
        assertEquals(TaskState.ENQUEUED, initial?.state)

        testChronos.cancelTaskById(req.id)
        val afterCancel = testChronos.observeTask(req.id).first()
        assertEquals(TaskState.CANCELLED, afterCancel?.state)
    }

    @Test
    fun testTagCancellationWorks() = runTest {
        val testChronos = TestChronos(registry)
        val req1 = OneTimeTaskRequest.Builder(SampleWorker::class).addTag("tag-a").build()
        val req2 = OneTimeTaskRequest.Builder(SampleWorker::class).addTag("tag-b").build()

        testChronos.enqueueTask("tag_a", UniqueTaskPolicy.REPLACE, req1)
        testChronos.enqueueTask("tag_b", UniqueTaskPolicy.REPLACE, req2)

        testChronos.cancelTaskByTag("tag-a")

        val info1 = testChronos.observeTask(req1.id).first()
        val info2 = testChronos.observeTask(req2.id).first()

        assertEquals(TaskState.CANCELLED, info1?.state)
        assertEquals(TaskState.ENQUEUED, info2?.state)
    }

    @Test
    fun testObserveTasksByTagFiltersProperly() = runTest {
        val testChronos = TestChronos(registry)
        val req1 = OneTimeTaskRequest.Builder(SampleWorker::class).addTag("shared").addTag("first").build()
        val req2 = OneTimeTaskRequest.Builder(SampleWorker::class).addTag("shared").addTag("second").build()
        val req3 = OneTimeTaskRequest.Builder(SampleWorker::class).addTag("other").build()

        testChronos.enqueueTask("shared_1", UniqueTaskPolicy.REPLACE, req1)
        testChronos.enqueueTask("shared_2", UniqueTaskPolicy.REPLACE, req2)
        testChronos.enqueueTask("other", UniqueTaskPolicy.REPLACE, req3)

        val sharedTasks = testChronos.observeTasksByTag("shared").first()
        assertEquals(2, sharedTasks.size)
        assertEquals(setOf(req1.id, req2.id), sharedTasks.map { it.id }.toSet())

        val firstTasks = testChronos.observeTasksByTag("first").first()
        assertEquals(1, firstTasks.size)
        assertEquals(req1.id, firstTasks.first().id)
    }

    @Test
    fun testRunPendingTasksNowExecutesAndSucceeds() = runTest {
        val testChronos = TestChronos(registry)
        val req = OneTimeTaskRequest.Builder(SampleWorker::class).build()

        testChronos.enqueueTask("run_pending", UniqueTaskPolicy.REPLACE, req)
        testChronos.runPendingTasksNow()

        val info = testChronos.observeTask(req.id).first()
        assertNotNull(info)
        assertEquals(TaskState.SUCCEEDED, info.state)
    }
}
