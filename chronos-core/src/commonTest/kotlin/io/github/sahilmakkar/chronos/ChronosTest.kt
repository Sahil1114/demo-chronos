package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.workDataOf
import io.github.sahilmakkar.chronos.worker.Worker
import io.github.sahilmakkar.chronos.worker.WorkerRegistry
import io.github.sahilmakkar.chronos.testing.*
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.TaskState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ChronosTest {

    @Test
    fun testWorkDataSerialization() {
        val original = workDataOf(
            "stringKey" to "hello world",
            "intKey" to 42,
            "longKey" to 1234567890L,
            "doubleKey" to 3.14159,
            "boolKey" to true
        )

        val json = original.toJson()
        val deserialized = TaskData.fromJson(json)

        assertEquals("hello world", deserialized.getString("stringKey"))
        assertEquals(expected = 42, actual = deserialized.getInt("intKey", 0))
        assertEquals(1234567890L, deserialized.getLong("longKey", 0L))
        assertEquals(3.14159, deserialized.getDouble("doubleKey", 0.0))
        assertEquals(true, deserialized.getBoolean("boolKey", false))
    }

    @Test
    fun testWorkDataSpecialCharacters() {
        val original = workDataOf(
            "escaped\"quote" to "value\"with\"quotes",
            "newlines" to "line1\nline2\r\nline3",
            "slashes" to "C:\\path\\to\\file"
        )

        val json = original.toJson()
        val deserialized = TaskData.fromJson(json)

        assertEquals("value\"with\"quotes", deserialized.getString("escaped\"quote"))
        assertEquals("line1\nline2\r\nline3", deserialized.getString("newlines"))
        assertEquals("C:\\path\\to\\file", deserialized.getString("slashes"))
    }

    class TestWorker(inputData: TaskData) : Worker(inputData) {
        override suspend fun doWork(): TaskResult {
            val num = inputData.getInt("num", 0)
            return if (num > 0) {
                TaskResult.Success(workDataOf("result" to num * 2))
            } else {
                TaskResult.Failure(workDataOf("error" to "invalid number"))
            }
        }
    }

    @Test
    fun testSynchronousExecution() = runTest {
        val registry = WorkerRegistry().apply {
            register("io.github.sahilmakkar.chronos.ChronosTest.TestWorker") { TestWorker(it) }
        }
        val scheduler = TestChronos(registry)

        val req = OneTimeTaskRequest.Builder(TestWorker::class)
            .setInputData(workDataOf("num" to 21))
            .build()

        scheduler.enqueue(req)
        scheduler.runPendingTasksNow()

        val info = scheduler.getWorkInfoFlow(req.id).first()
        assertNotNull(info)
        assertEquals(TaskState.SUCCEEDED, info.state)
        assertEquals(42, info.outputData.getInt("result", 0))
    }

    @Test
    fun testFailedWorkerSynchronous() = runTest {
        val registry = WorkerRegistry().apply {
            register("io.github.sahilmakkar.chronos.ChronosTest.TestWorker") { TestWorker(it) }
        }
        val scheduler = TestChronos(registry)

        val req = OneTimeTaskRequest.Builder(TestWorker::class)
            .setInputData(workDataOf("num" to 0))
            .build()

        scheduler.enqueue(req)
        scheduler.runPendingTasksNow()

        val info = scheduler.getWorkInfoFlow(req.id).first()
        assertNotNull(info)
        assertEquals(TaskState.FAILED, info.state)
        assertEquals("invalid number", info.outputData.getString("error"))
    }
}
