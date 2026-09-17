package io.github.sahilmakkar.chronos.task

import io.github.sahilmakkar.chronos.task.policy.DependentTaskPolicy
import io.github.sahilmakkar.chronos.task.request.TaskRequest

class TaskChain(
    val requests: List<TaskRequest>,
    val failurePolicies: List<DependentTaskPolicy>
) {

    fun then(
        request: TaskRequest,
        onFailure: DependentTaskPolicy = DependentTaskPolicy.STOP_ON_FAILURE
    ): TaskChain {
        return TaskChain(
            requests = requests + request,
            failurePolicies = failurePolicies + onFailure
        )
    }

    companion object {
        fun start(request: TaskRequest): TaskChain = TaskChain(
            requests = listOf(request),
            failurePolicies = emptyList()
        )
    }
}