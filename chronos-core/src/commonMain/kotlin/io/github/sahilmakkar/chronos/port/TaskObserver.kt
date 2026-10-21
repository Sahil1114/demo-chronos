package io.github.sahilmakkar.chronos.port

import io.github.sahilmakkar.chronos.task.model.TaskInfo
import kotlinx.coroutines.flow.Flow

interface TaskObserver {
    fun observeTask(id: String): Flow<TaskInfo?>
    fun observeTasksByTag(tag: String): Flow<List<TaskInfo>>

    @Deprecated("Use observeTask instead", ReplaceWith("observeTask(id)"))
    fun getWorkInfoFlow(id: String): Flow<TaskInfo?> = observeTask(id)

    @Deprecated("Use observeTasksByTag instead", ReplaceWith("observeTasksByTag(tag)"))
    fun getWorkInfosByTagFlow(tag: String): Flow<List<TaskInfo>> = observeTasksByTag(tag)
}

@Deprecated("Use TaskObserver instead", ReplaceWith("TaskObserver"))
typealias WorkObserver = TaskObserver
