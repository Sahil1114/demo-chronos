package io.github.sahilmakkar.chronos.task.model

import io.github.sahilmakkar.chronos.task.TaskState

/**
 * Immutable snapshot of a task's current execution status, diagnostic metadata, and payloads.
 *
 * `TaskInfo` instances are emitted reactively by [io.github.sahilmakkar.chronos.Chronos.observeTask]
 * and [io.github.sahilmakkar.chronos.Chronos.observeTasksByTag] whenever a task transitions between states,
 * reports incremental progress, or completes.
 *
 * ### Lifecycle States
 * Inspect [state] to track task progress:
 * - [TaskState.ENQUEUED]: Waiting for scheduled time or prerequisite constraints.
 * - [TaskState.RUNNING]: Currently executing inside a [io.github.sahilmakkar.chronos.worker.TaskWorker].
 * - [TaskState.SUCCEEDED]: Completed successfully; final output payload is available in [outputData].
 * - [TaskState.FAILED]: Terminated permanently after exhausting retry attempts.
 * - [TaskState.RETRYING]: Failed temporarily and waiting for backoff delay before running again.
 * - [TaskState.CANCELLED]: Cancelled explicitly by caller or transitively through a cancelled parent task.
 *
 * @property id The canonical UUID string identifying this task uniquely.
 * @property state The current lifecycle state of the task.
 * @property outputData The key-value payload produced upon completion or failure. Empty if running or enqueued.
 * @property progress The latest intermediate progress payload emitted by the running worker via [io.github.sahilmakkar.chronos.worker.TaskWorker.setProgress].
 * @property runAttemptCount The number of times execution has been attempted for this task. Starts at `0` upon enqueue.
 */
public data class TaskInfo(
    public val id: String,
    public val state: TaskState,
    public val outputData: TaskData,
    public val progress: TaskData,
    public val runAttemptCount: Int
)
