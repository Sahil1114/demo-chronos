package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.TaskState
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.fromJson
import io.github.sahilmakkar.chronos.task.model.taskDataOf
import io.github.sahilmakkar.chronos.task.model.toJson
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import io.github.sahilmakkar.chronos.testing.TestChronos
import io.github.sahilmakkar.chronos.worker.TaskWorker
import io.github.sahilmakkar.chronos.worker.WorkerRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ChronosTest {

    @Test
    fun testWorkDataSerialization() {
        val original = taskDataOf(
            "stringKey" to "hello world",
            "intKey" to 42,
            "longKey" to 1234567890L,
            "doubleKey" to 3.14159,
            "boolKey" to true
        )

        val json = original.toJson()
        val deserialized = TaskData.fromJson(json)

        assertEquals(expected = "hello world", actual = deserialized.getString(key = "stringKey"))
        assertEquals(expected = 42, actual = deserialized.getInt(key = "intKey", defaultValue = 0))
        assertEquals(expected = 1234567890L, actual = deserialized.getLong(
            key = "longKey",
            defaultValue = 0L
        ))
        assertEquals(expected = 3.14159, actual = deserialized.getDouble(
            key = "doubleKey",
            defaultValue = 0.0
        ))
        assertEquals(expected = true, actual = deserialized.getBoolean(
            key = "boolKey",
            defaultValue = false
        ))
    }

    @Test
    fun testWorkDataSpecialCharacters() {
        val original = taskDataOf(
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

    class TestWorker(inputData: TaskData) : TaskWorker(inputData) {
        override suspend fun doWork(): TaskResult {
            val num = inputData.getInt("num", 0)
            return if (num > 0) {
                TaskResult.Success(taskDataOf("result" to num * 2))
            } else {
                TaskResult.Failure(taskDataOf("error" to "invalid number"))
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
            .setInputData(taskDataOf("num" to 21))
            .build()

        scheduler.enqueue(req)
        scheduler.runPendingTasksNow()

        val info = scheduler.observeTask(req.id).first()
        assertNotNull(actual = info)
        assertEquals(expected = TaskState.SUCCEEDED, actual = info.state)
        assertEquals(expected = 42, actual = info.outputData.getInt("result", 0))
    }

    @Test
    fun testFailedWorkerSynchronous() = runTest {
        val registry = WorkerRegistry().apply {
            register(className = "io.github.sahilmakkar.chronos.ChronosTest.TestWorker") { TestWorker(it) }
        }
        val scheduler = TestChronos(registry)

        val req = OneTimeTaskRequest.Builder(TestWorker::class)
            .setInputData(taskDataOf("num" to 0))
            .build()

        scheduler.enqueue(request = req)
        scheduler.runPendingTasksNow()

        val info = scheduler.observeTask(id = req.id).first()
        assertNotNull(actual = info)
        assertEquals(expected = TaskState.FAILED, actual = info.state)
        assertEquals(expected = "invalid number", actual = info.outputData.getString("error"))
    }
}
