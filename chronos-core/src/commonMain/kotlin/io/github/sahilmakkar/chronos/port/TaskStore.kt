package io.github.sahilmakkar.chronos.port

import io.github.sahilmakkar.chronos.task.model.TaskInfo
import kotlinx.coroutines.flow.Flow

/**
 * Internal architecture port defining reactive persistence storage operations for task states.
 *
 * Exposes observable reactive queries from the persistent database to the engine layer,
 * mapping lower-level entity models into presentation-ready [TaskInfo] snapshots.
 */
internal interface TaskStore {

    /**
     * Emits state snapshots for the task matching [id].
     *
     * @param id The canonical UUID string of the task.
     * @return A cold [Flow] that emits [TaskInfo] snapshots whenever the task record changes,
     *   or `null` if the record does not exist.
     */
    fun observeTask(id: String): Flow<TaskInfo?>

    /**
     * Emits updated lists of state snapshots for all tasks carrying [tag].
     *
     * @param tag The tag string associated with the tasks.
     * @return A cold [Flow] emitting lists of [TaskInfo] records whenever any matching task changes.
     */
    fun observeTasksByTag(tag: String): Flow<List<TaskInfo>>
}
