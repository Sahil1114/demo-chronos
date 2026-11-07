package io.github.sahilmakkar.chronos.port

import io.github.sahilmakkar.chronos.task.model.TaskInfo
import kotlinx.coroutines.flow.Flow

/**
 * Port contract for observing task status and progression flows.
 */
internal interface TaskObserver {
    fun observeTask(id: String): Flow<TaskInfo?>
    fun observeTasksByTag(tag: String): Flow<List<TaskInfo>>
}
