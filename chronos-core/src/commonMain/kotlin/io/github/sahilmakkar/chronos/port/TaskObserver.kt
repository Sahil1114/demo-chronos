package io.github.sahilmakkar.chronos.port

import io.github.sahilmakkar.chronos.task.model.TaskInfo
import kotlinx.coroutines.flow.Flow

/**
 * Internal architecture port defining reactive task status observation streams.
 *
 * Provides callers with hot [Flow] streams backed by Room SQLite invalidation trackers,
 * emitting new snapshots whenever a task's state, progress, or output data changes.
 */
internal interface TaskObserver {

    /**
     * Observes real-time execution snapshots for a specific task identified by [id].
     *
     * Emits the initial task state upon subscription, followed by subsequent updates whenever
     * the task's state, progress, or output data is modified in the database.
     * If no task exists matching [id], emits `null`.
     *
     * @param id The canonical UUID string of the task to observe.
     * @return A cold [Flow] that emits [TaskInfo] snapshots or `null` if the task does not exist.
     */
    fun observeTask(id: String): Flow<TaskInfo?>

    /**
     * Observes real-time execution snapshots for all tasks carrying the specified [tag].
     *
     * Emits an updated list whenever any task matching the tag transitions state or reports progress.
     * If no matching tasks exist, emits an empty list.
     *
     * @param tag The tag string identifying tasks to monitor.
     * @return A cold [Flow] emitting lists of [TaskInfo] records.
     */
    fun observeTasksByTag(tag: String): Flow<List<TaskInfo>>
}
