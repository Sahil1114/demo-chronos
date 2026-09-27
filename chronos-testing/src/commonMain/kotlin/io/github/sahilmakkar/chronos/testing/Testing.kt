package io.github.sahilmakkar.chronos.testing

import io.github.sahilmakkar.chronos.Chronos
import io.github.sahilmakkar.chronos.task.TaskChain
import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.TaskState
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.TaskInfo
import io.github.sahilmakkar.chronos.task.model.workDataOf
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
    private val workInfos = MutableStateFlow<Map<String, TaskInfo>>(emptyMap())


    override fun enqueue(request: TaskRequest) {
        enqueuedRequests.add(request)
        updateWorkInfo(request.id, TaskState.ENQUEUED)
    }

    override fun enqueueUniqueWork(name: String, policy: UniqueTaskPolicy, request: OneTimeTaskRequest) {
        enqueue(request = request)
    }

    override fun enqueueUniquePeriodicWork(name: String, policy: UniqueTaskPolicy, request: PeriodicTaskRequest) {
        enqueue(request = request)
    }

    override fun enqueue(chain: TaskChain) {
        chain.requests.forEach { enqueue(request = it) }
    }

    override fun cancelWorkById(id: String) {
        updateWorkInfo(id = id, state = TaskState.CANCELLED)
    }

    override fun cancelWorkByTag(tag: String) {
        // Tag cancellation not fully fleshed in stub
    }

    override fun cancelAllWork() {
        workInfos.value.keys.forEach { cancelWorkById(id = it) }
    }

    override fun getWorkInfoFlow(id: String): Flow<TaskInfo?> {
        return workInfos.map { it[id] }
    }

    override fun getWorkInfosByTagFlow(tag: String): Flow<List<TaskInfo>> {
        return workInfos.map { it.values.toList() }
    }

    override suspend fun runPendingTasksNow() {
        val iterator = enqueuedRequests.iterator()
        while (iterator.hasNext()) {
            val req = iterator.next()
            updateWorkInfo(req.id, TaskState.RUNNING)
            try {
                val worker = registry.createWorker(req.workerClassName, req.taskData)
                val res = worker.doWork()
                when (res) {
                    is TaskResult.Success -> updateWorkInfo(
                        id = req.id,
                        state = TaskState.SUCCEEDED,
                        output = res.outputData
                    )
                    is TaskResult.Failure -> updateWorkInfo(
                        id = req.id,
                        state = TaskState.FAILED,
                        output = res.outputData
                    )
                    TaskResult.Retry -> updateWorkInfo(id = req.id, state = TaskState.RETRYING)
                }
            } catch (e: Exception) {
                updateWorkInfo(id = req.id, state = TaskState.FAILED)
            }
            iterator.remove()
        }
    }

    override fun close() {
        enqueuedRequests.clear()
        workInfos.value = emptyMap()
    }

    private fun updateWorkInfo(id: String, state: TaskState, output: TaskData = workDataOf()) {
        val current = workInfos.value[id]
        val attempt = (current?.runAttemptCount ?: 0) + (if (state == TaskState.RUNNING) 1 else 0)
        val newInfo = TaskInfo(
            id = id,
            state = state,
            outputData = output,
            progress = current?.progress ?: workDataOf(),
            runAttemptCount = attempt
        )
        workInfos.value = workInfos.value + (id to newInfo)
    }
}