package io.github.sahilmakkar.chronos.storage

import io.github.sahilmakkar.chronos.port.TaskStore
import io.github.sahilmakkar.chronos.storage.mapper.toTaskInfo
import io.github.sahilmakkar.chronos.task.model.TaskInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Production implementation of [TaskStore] providing reactive data observation flows backed by [TaskDao].
 *
 * Automatically converts internal database [TaskEntity] records into public [TaskInfo] snapshots,
 * leveraging Room's built-in SQLite table invalidation tracker for low-overhead push notifications.
 *
 * @param dao The Room Data Access Object providing SQLite query access.
 */
internal class RoomTaskStore(
    private val dao: TaskDao
) : TaskStore {

    /**
     * Subscribes to database changes for a specific task identified by [id].
     *
     * Maps the emitted [TaskEntity] to a public [TaskInfo] snapshot. Emits `null` if no record
     * is found with [id].
     *
     * @param id The canonical UUID string of the task.
     * @return A [Flow] streaming the task's state changes.
     */
    override fun observeTask(id: String): Flow<TaskInfo?> =
        dao.observeTask(id = id).map { it?.toTaskInfo() }

    /**
     * Subscribes to database changes for all tasks bearing the specified [tag].
     *
     * Maps the emitted list of [TaskEntity] records to a list of [TaskInfo] instances.
     *
     * @param tag The tag name used for grouping tasks.
     * @return A [Flow] streaming lists of task snapshots.
     */
    override fun observeTasksByTag(tag: String): Flow<List<TaskInfo>> =
        dao.observeTasksByTag(tag = tag).map { list -> list.map { it.toTaskInfo() } }
}
