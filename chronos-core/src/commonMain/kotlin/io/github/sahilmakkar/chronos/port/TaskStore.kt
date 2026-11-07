package io.github.sahilmakkar.chronos.port

import io.github.sahilmakkar.chronos.task.model.TaskInfo
import kotlinx.coroutines.flow.Flow

/**
 * Port contract for task state querying and observation.
 */
internal interface TaskStore {
    fun observeTask(id: String): Flow<TaskInfo?>
    fun observeTasksByTag(tag: String): Flow<List<TaskInfo>>
}
