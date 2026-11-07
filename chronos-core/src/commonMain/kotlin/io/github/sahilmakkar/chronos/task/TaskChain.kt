package io.github.sahilmakkar.chronos.task

import io.github.sahilmakkar.chronos.task.policy.DependentTaskPolicy
import io.github.sahilmakkar.chronos.task.request.TaskRequest

/**
 * Represents a sequence of dependent tasks that execute in strict order.
 *
 * @property requests List of ordered task requests in the chain.
 * @property failurePolicies Corresponding failure policies for dependent tasks.
 */
public class TaskChain(
    public val requests: List<TaskRequest>,
    public val failurePolicies: List<DependentTaskPolicy>
) {

    /**
     * Appends a subsequent task request to this execution chain.
     */
    public fun then(
        request: TaskRequest,
        onFailure: DependentTaskPolicy = DependentTaskPolicy.STOP_ON_FAILURE
    ): TaskChain {
        return TaskChain(
            requests = requests + request,
            failurePolicies = failurePolicies + onFailure
        )
    }

    public companion object {
        /**
         * Begins a new chain starting with the specified task request.
         */
        public fun start(request: TaskRequest): TaskChain = TaskChain(
            requests = listOf(request),
            failurePolicies = emptyList()
        )
    }
}
