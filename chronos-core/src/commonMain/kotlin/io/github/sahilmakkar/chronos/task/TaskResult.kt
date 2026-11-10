package io.github.sahilmakkar.chronos.task

import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.taskDataOf

/**
 * Terminal or retry outcome returned by a [io.github.sahilmakkar.chronos.worker.TaskWorker] upon completing [io.github.sahilmakkar.chronos.worker.TaskWorker.doWork].
 *
 * The coordinator inspects the returned [TaskResult] to orchestrate state transitions, retry scheduling,
 * chain advancement, and periodic rescheduling.
 *
 * @see Success
 * @see Failure
 * @see Retry
 */
public sealed class TaskResult {

    /**
     * Indicates that the worker completed its execution successfully.
     *
     * Transitions the task to [TaskState.SUCCEEDED], stores [outputData] in the database,
     * triggers any downstream dependent tasks waiting in a [TaskChain], and reschedules recurring
     * runs if the task is periodic.
     *
     * @property outputData Optional key-value payload containing results or telemetry produced by the worker.
     */
    public data class Success(public val outputData: TaskData = taskDataOf()) : TaskResult()

    /**
     * Indicates that the worker encountered an unrecoverable failure.
     *
     * Transitions the task to [TaskState.FAILED] and persists [outputData] for diagnostic inspection.
     * If the task is part of a [TaskChain], downstream tasks are halted or continued depending on
     * [io.github.sahilmakkar.chronos.task.policy.DependentTaskPolicy].
     *
     * If the task is a [io.github.sahilmakkar.chronos.task.request.PeriodicTaskRequest], the task is
     * automatically rescheduled for its next period interval in the `ENQUEUED` state after recording the failure.
     *
     * @property outputData Optional diagnostic payload containing error messages, stack details, or failure context.
     */
    public data class Failure(public val outputData: TaskData = taskDataOf()) : TaskResult()

    /**
     * Indicates that the worker encountered a transient failure and requests a retry.
     *
     * If the task has remaining retries (`runAttemptCount <= maxRetries`), Chronos calculates
     * a retry delay using the configured [io.github.sahilmakkar.chronos.task.policy.BackoffPolicy],
     * transitions the task to [TaskState.RETRYING], and updates the scheduled wake time.
     *
     * If all retries are exhausted, the task is marked as permanently [TaskState.FAILED].
     */
    public data object Retry : TaskResult()
}
