package io.github.sahilmakkar.chronos.port

import io.github.sahilmakkar.chronos.task.model.TaskInfo
import kotlinx.coroutines.flow.Flow

internal interface TaskObserver {
    fun observeTask(id: String): Flow<TaskInfo?>
    fun observeTasksByTag(tag: String): Flow<List<TaskInfo>>
}
