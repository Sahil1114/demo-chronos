package io.github.sahilmakkar.chronos.task

/**
 * Lifecycle state representation for background tasks managed by Chronos.
 *
 * ### State Transition Lifecycle:
 * ```
 *                 ┌────────────────────────────────────────────────────────┐
 *                 │                                                        │
 *                 ▼                                                        │
 * [ENQUEUED] ──► [RUNNING] ──┬──► [SUCCEEDED]                              │
 *     │             │        │                                             │
 *     │             │        ├──► [FAILED]                                 │
 *     │             │        │                                             │
 *     │             │        └──► [RETRYING] ──(backoff delay expires)─────┘
 *     │             │                   │
 *     ▼             ▼                   ▼
 * [──────────────────── CANCELLED ────────────────────]
 * ```
 *
 * - **Active states:** [ENQUEUED], [RUNNING], and [RETRYING].
 * - **Terminal states (one-time tasks):** [SUCCEEDED], [FAILED], and [CANCELLED].
 *
 * @see io.github.sahilmakkar.chronos.task.model.TaskInfo
 * @see io.github.sahilmakkar.chronos.Chronos.observeTask
 */
public enum class TaskState {
    /**
     * The task has been durably enqueued in the database and is awaiting execution eligibility.
     *
     * A task in this state will remain pending until:
     * 1. Any configured initial delay has elapsed (`scheduledAt <= now`).
     * 2. All parent tasks in a [TaskChain] have completed successfully.
     * 3. All configured [io.github.sahilmakkar.chronos.task.constraints.EnvironmentConstraints] are satisfied.
     */
    ENQUEUED,

    /**
     * The task has been claimed atomically by the coordinator and is currently executing within
     * its assigned [io.github.sahilmakkar.chronos.worker.TaskWorker].
     *
     * Tasks in this state hold a concurrency permit from the coordinator's execution semaphore.
     */
    RUNNING,

    /**
     * The task executed successfully and returned [TaskResult.Success].
     *
     * This is a terminal state for one-time tasks. Downstream dependent tasks in an execution chain
     * will now be unblocked. Periodic tasks reschedule another `ENQUEUED` run after a success.
     */
    SUCCEEDED,

    /**
     * The task failed to execute or exhausted all configured retry attempts.
     *
     * For one-time tasks, this is a terminal state. Diagnostic output data may be inspected via [io.github.sahilmakkar.chronos.task.model.TaskInfo.outputData].
     */
    FAILED,

    /**
     * The task execution failed or requested retry via [TaskResult.Retry], and has remaining retry attempts.
     *
     * The task is scheduled to re-enter eligibility at a future timestamp determined by the
     * task's backoff criteria. While waiting, it does not hold execution concurrency permits.
     */
    RETRYING,

    /**
     * The task was explicitly cancelled via [io.github.sahilmakkar.chronos.Chronos.cancelTaskById],
     * [io.github.sahilmakkar.chronos.Chronos.cancelTaskByTag], or cascading chain cancellation.
     *
     * This is a permanent terminal state; cancelled tasks are never re-run or rescheduled.
     */
    CANCELLED
}
