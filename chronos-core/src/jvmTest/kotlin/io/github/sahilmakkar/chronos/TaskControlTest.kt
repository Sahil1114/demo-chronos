package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.storage.RoomTaskStore
import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.TaskState
import io.github.sahilmakkar.chronos.task.model.taskDataOf
import io.github.sahilmakkar.chronos.task.model.toJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration

class TaskControlTest {

    private lateinit var h: TestHarness
    private lateinit var store: RoomTaskStore

    @BeforeTest
    fun setUp() {
        h = TestHarness()
        store = RoomTaskStore(h.db.taskDao())
    }

    @AfterTest
    fun tearDown() = runBlocking {
        h.close()
    }

    @Test
    fun `cancel by id marks task cancelled`() = runTest {
        val req = h.request(initialDelay = Duration.parse("1h"))
        h.enqueue(req)
        h.cancelTaskById(req.id)

        val entity = h.db.taskDao().getTask(req.id)
        assertEquals(TaskState.CANCELLED, entity?.state)
    }

    @Test
    fun `cancel by tag cancels matching tasks only`() = runTest {
        val req1 = h.request(initialDelay = Duration.parse("1h"), tags = setOf("tagA"))
        val req2 = h.request(initialDelay = Duration.parse("1h"), tags = setOf("tagB"))
        val req3 = h.request(initialDelay = Duration.parse("1h"), tags = setOf("tagA", "tagB"))

        h.enqueue(req1)
        h.enqueue(req2)
        h.enqueue(req3)

        h.cancelTaskByTag("tagA")

        assertEquals(TaskState.CANCELLED, h.db.taskDao().getTask(req1.id)?.state)
        assertEquals(TaskState.ENQUEUED, h.db.taskDao().getTask(req2.id)?.state)
        assertEquals(TaskState.CANCELLED, h.db.taskDao().getTask(req3.id)?.state)
    }

    @Test
    fun `cancel all cancels all active tasks`() = runTest {
        val req1 = h.request(initialDelay = Duration.parse("1h"))
        val req2 = h.request(initialDelay = Duration.parse("1h"))

        h.enqueue(req1)
        h.enqueue(req2)

        h.cancelAllTasks()

        assertEquals(TaskState.CANCELLED, h.db.taskDao().getTask(req1.id)?.state)
        assertEquals(TaskState.CANCELLED, h.db.taskDao().getTask(req2.id)?.state)
    }

    @Test
    fun `cancel downstream tasks in chain when parent cancelled`() = runTest {
        val parent = h.request(initialDelay = Duration.parse("1h"))
        val child = h.request()

        h.enqueueChain(parent, child)
        h.cancelTaskById(parent.id)

        assertEquals(TaskState.CANCELLED, h.db.taskDao().getTask(parent.id)?.state)
        assertEquals(TaskState.CANCELLED, h.db.taskDao().getTask(child.id)?.state)
    }

    @Test
    fun `cancel does not overwrite finished tasks`() = runTest {
        val req = h.request()
        h.enqueue(req)
        h.runPendingTasks()

        assertEquals(TaskState.SUCCEEDED, h.db.taskDao().getTask(req.id)?.state)

        h.cancelTaskById(req.id)
        assertEquals(TaskState.SUCCEEDED, h.db.taskDao().getTask(req.id)?.state)
    }

    @Test
    fun `cancel while running leaves task in cancelled state`() = runTest {
        val req = h.request()
        var cancelTriggered = false

        h.executor.script = { entity ->
            if (!cancelTriggered) {
                cancelTriggered = true
                runBlocking {
                    h.cancelTaskById(entity.id)
                }
            }
            TaskResult.Success()
        }

        h.enqueue(req)
        h.runPendingTasks()

        val entity = h.db.taskDao().getTask(req.id)
        assertEquals(TaskState.CANCELLED, entity?.state)
    }

    @Test
    fun `observeTask emits task states as it executes`() = runTest {
        val req = h.request(initialDelay = Duration.parse("1h"))
        h.enqueue(req)

        val enqueuedInfo = store.observeTask(req.id).first()
        assertNotNull(enqueuedInfo)
        assertEquals(TaskState.ENQUEUED, enqueuedInfo.state)

        h.clock.advance(3600_000L)
        h.runPendingTasks()

        val succeededInfo = store.observeTask(req.id).first()
        assertNotNull(succeededInfo)
        assertEquals(TaskState.SUCCEEDED, succeededInfo.state)
    }

    @Test
    fun `observeTasksByTag emits filtered tasks`() = runTest {
        val req1 = h.request(tags = setOf("sync"))
        val req2 = h.request(tags = setOf("backup"))
        val req3 = h.request(tags = setOf("sync", "urgent"))

        h.enqueue(req1)
        h.enqueue(req2)
        h.enqueue(req3)

        val syncTasks = store.observeTasksByTag("sync").first()
        val syncIds = syncTasks.map { it.id }.toSet()

        assertEquals(2, syncTasks.size)
        assertEquals(setOf(req1.id, req3.id), syncIds)
    }

    @Test
    fun `progress updates reach the flow`() = runTest {
        val req = h.request()
        h.enqueue(req)

        h.db.taskDao().updateTaskProgress(req.id, taskDataOf("progress" to 50).toJson())

        val info = store.observeTask(req.id).first()
        assertNotNull(info)
        assertEquals(50, info.progress.getInt("progress", 0))
    }
}
