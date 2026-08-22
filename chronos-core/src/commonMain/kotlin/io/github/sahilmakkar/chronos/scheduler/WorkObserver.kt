package io.github.sahilmakkar.chronos.scheduler

import io.github.sahilmakkar.chronos.task.model.TaskInfo
import kotlinx.coroutines.flow.Flow


/** Observes work state changes. */
interface WorkObserver {
    fun getWorkInfoFlow(id: String): Flow<TaskInfo?>
    fun getWorkInfosByTagFlow(tag: String): Flow<List<TaskInfo>>
}
