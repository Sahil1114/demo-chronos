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
     *
     * @param request The subsequent task to execute once prior tasks succeed.
     * @param onFailure Behavior policy if the prior task fails (defaults to [DependentTaskPolicy.STOP_ON_FAILURE]).
     * @return New [TaskChain] containing the appended task step.
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
         *
         * @param request The initial task request in the chain.
         * @return A new single-step [TaskChain].
         */
        public fun start(request: TaskRequest): TaskChain = TaskChain(
            requests = listOf(request),
            failurePolicies = emptyList()
        )
    }
}
