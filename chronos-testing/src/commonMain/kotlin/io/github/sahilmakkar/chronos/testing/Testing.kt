package io.github.sahilmakkar.chronos.testing

import io.github.sahilmakkar.chronos.Chronos
import io.github.sahilmakkar.chronos.task.TaskChain
import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.TaskState
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.TaskInfo
import io.github.sahilmakkar.chronos.task.model.taskDataOf
import io.github.sahilmakkar.chronos.task.policy.UniqueTaskPolicy
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import io.github.sahilmakkar.chronos.task.request.PeriodicTaskRequest
import io.github.sahilmakkar.chronos.task.request.TaskRequest
import io.github.sahilmakkar.chronos.worker.WorkerRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class TestChronos(
    override val registry: WorkerRegistry = WorkerRegistry(),
) : Chronos {
    val enqueuedRequests = mutableListOf<TaskRequest>()
    private val taskTags = mutableMapOf<String, Set<String>>()
    private val taskInfos = MutableStateFlow<Map<String, TaskInfo>>(emptyMap())

    override fun enqueue(request: TaskRequest) {
        enqueuedRequests.add(request)
        taskTags[request.id] = request.tags
        updateTaskInfo(request.id, TaskState.ENQUEUED)
    }

    override fun enqueueUniqueTask(name: String, policy: UniqueTaskPolicy, request: OneTimeTaskRequest) {
        enqueue(request = request)
    }

    override fun enqueueUniquePeriodicTask(name: String, policy: UniqueTaskPolicy, request: PeriodicTaskRequest) {
        enqueue(request = request)
    }

    override fun enqueue(chain: TaskChain) {
        chain.requests.forEach { enqueue(request = it) }
    }

    override fun cancelTaskById(id: String) {
        updateTaskInfo(id = id, state = TaskState.CANCELLED)
    }

    override fun cancelTaskByTag(tag: String) {
        val idsToCancel = taskTags.filter { (_, tags) -> tag in tags }.keys
        idsToCancel.forEach { cancelTaskById(it) }
    }

    override fun cancelAllTasks() {
        taskInfos.value.keys.forEach { cancelTaskById(id = it) }
    }

    override fun observeTask(id: String): Flow<TaskInfo?> {
        return taskInfos.map { it[id] }
    }

    override fun observeTasksByTag(tag: String): Flow<List<TaskInfo>> {
        return taskInfos.map { map ->
            val matchingIds = taskTags.filter { (_, tags) -> tag in tags }.keys
            matchingIds.mapNotNull { map[it] }
        }
    }

    override suspend fun runPendingTasksNow() {
        val iterator = enqueuedRequests.iterator()
        while (iterator.hasNext()) {
            val req = iterator.next()
            updateTaskInfo(req.id, TaskState.RUNNING)
            try {
                val worker = registry.createWorker(req.workerClassName, req.taskData)
                val res = worker.doWork()
                when (res) {
                    is TaskResult.Success -> updateTaskInfo(
                        id = req.id,
                        state = TaskState.SUCCEEDED,
                        output = res.outputData
                    )
                    is TaskResult.Failure -> updateTaskInfo(
                        id = req.id,
                        state = TaskState.FAILED,
                        output = res.outputData
                    )
                    TaskResult.Retry -> updateTaskInfo(id = req.id, state = TaskState.RETRYING)
                }
            } catch (e: Exception) {
                updateTaskInfo(id = req.id, state = TaskState.FAILED)
            }
            iterator.remove()
        }
    }

    override fun close() {
        enqueuedRequests.clear()
        taskTags.clear()
        taskInfos.value = emptyMap()
    }

    private fun updateTaskInfo(id: String, state: TaskState, output: TaskData = taskDataOf()) {
        val current = taskInfos.value[id]
        val attempt = (current?.runAttemptCount ?: 0) + (if (state == TaskState.RUNNING) 1 else 0)
        val newInfo = TaskInfo(
            id = id,
            state = state,
            outputData = output,
            progress = current?.progress ?: taskDataOf(),
            runAttemptCount = attempt
        )
        taskInfos.value += (id to newInfo)
    }
}
