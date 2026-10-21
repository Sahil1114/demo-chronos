package io.github.sahilmakkar.chronos.storage

import io.github.sahilmakkar.chronos.port.TaskStore
import io.github.sahilmakkar.chronos.storage.mapper.toTaskInfo
import io.github.sahilmakkar.chronos.task.model.TaskInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomTaskStore(
    private val dao: TaskDao
) : TaskStore {

    override fun observeTask(id: String): Flow<TaskInfo?> =
        dao.observeTask(id).map { it?.toTaskInfo() }

    override fun observeTasksByTag(tag: String): Flow<List<TaskInfo>> =
        dao.observeTasksByTag(tag).map { list -> list.map { it.toTaskInfo() } }
}

@Deprecated("Use RoomTaskStore instead", ReplaceWith("RoomTaskStore"))
internal typealias RoomWorkInfoStore = RoomTaskStore
