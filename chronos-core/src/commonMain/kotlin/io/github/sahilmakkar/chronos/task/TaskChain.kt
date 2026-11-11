package io.github.sahilmakkar.chronos.task

import io.github.sahilmakkar.chronos.task.policy.DependentTaskPolicy
import io.github.sahilmakkar.chronos.task.request.TaskRequest

/**
 * Represents a sequence of dependent tasks executed in strict sequential order.
 *
 * Each task in the chain depends on the successful completion of its immediate predecessor.
 * All tasks, tags, and parent-child dependency edges in a chain are inserted into the database
 * within a single atomic database transaction.
 *
 * ### Execution and Failure Handling:
 * - A downstream task remains [TaskState.ENQUEUED] until its parent reaches [TaskState.SUCCEEDED].
 * - If a parent task fails or exhausts retries, downstream tasks are evaluated per their [DependentTaskPolicy]:
 *   - [DependentTaskPolicy.STOP_ON_FAILURE]: Downstream tasks are marked [TaskState.FAILED] and skipped.
 *   - [DependentTaskPolicy.CONTINUE_ON_FAILURE]: Downstream tasks are released and executed normally.
 * - If a task in a chain is cancelled, downstream tasks are recursively marked [TaskState.CANCELLED].
 *
 * ### Usage Example:
 * ```kotlin
 * val chain = TaskChain.start(fetchDataTask)
 *     .then(parseDataTask, onFailure = DependentTaskPolicy.STOP_ON_FAILURE)
 *     .then(notifyUserTask, onFailure = DependentTaskPolicy.CONTINUE_ON_FAILURE)
 *
 * chronos.enqueue(chain)
 * ```
 *
 * @property requests List of ordered task requests comprising the chain.
 * @property failurePolicies Corresponding failure handling policies for inter-task dependency edges.
 *
 * @see DependentTaskPolicy
 * @see io.github.sahilmakkar.chronos.Chronos.beginWith
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
