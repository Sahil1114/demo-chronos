package io.github.sahilmakkar.chronos.task

import io.github.sahilmakkar.chronos.Chronos
import io.github.sahilmakkar.chronos.task.policy.DependentTaskPolicy
import io.github.sahilmakkar.chronos.task.request.TaskRequest

/**
 * Represents a sequentially dependent chain of [io.github.sahilmakkar.chronos.task.request.TaskRequest]s.
 * Chains are created using [io.github.sahilmakkar.chronos.beginWith] and appended using [then].
 *
 * @property requests The ordered list of requests to execute.
 * @property failurePolicies The policies dictating behavior when parent tasks fail.
 */
class TaskChain(
    val chronos: Chronos,
    val requests: List<TaskRequest>,
    val failurePolicies: List<DependentTaskPolicy>
) {

    /**
     * Appends a new [TaskRequest] to this dependency chain.
     *
     * @param request The task request to append.
     * @param onFailure Policy specifying behavior if this step fails before running the child.
     * @return The updated [TaskChain].
     */
    fun then(request: TaskRequest, onFailure: DependentTaskPolicy = DependentTaskPolicy.STOP_ON_FAILURE): TaskChain {
        return TaskChain(
            chronos = chronos,
            requests = requests + request,
            failurePolicies = failurePolicies + onFailure
        )
    }

    /**
     * Enqueues this chain for background execution using the singleton [Chronos] scheduler.
     */
    fun enqueue() {
        chronos.enqueue(this)
    }
}