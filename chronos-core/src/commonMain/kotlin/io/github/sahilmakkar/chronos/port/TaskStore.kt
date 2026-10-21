package io.github.sahilmakkar.chronos.port

import io.github.sahilmakkar.chronos.task.model.TaskInfo
import kotlinx.coroutines.flow.Flow

internal interface TaskStore {
    fun observeTask(id: String): Flow<TaskInfo?>
    fun observeTasksByTag(tag: String): Flow<List<TaskInfo>>

    // Compatibility aliases
    fun observeWorkInfo(id: String): Flow<TaskInfo?> = observeTask(id)
    fun observeWorkInfosByTag(tag: String): Flow<List<TaskInfo>> = observeTasksByTag(tag)
}

@Deprecated("Use TaskStore instead", ReplaceWith("TaskStore"))
internal typealias WorkInfoStore = TaskStore
