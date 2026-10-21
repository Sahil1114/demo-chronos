package io.github.sahilmakkar.chronos.storage.converter

import androidx.room3.ColumnTypeConverter
import io.github.sahilmakkar.chronos.task.TaskState

class TaskStateConverter {
    @ColumnTypeConverter
    fun fromTaskState(taskState: TaskState): String {
        return taskState.name
    }

    @ColumnTypeConverter
    fun toTaskState(taskState: String): TaskState {
        return TaskState.valueOf(taskState)
    }
}
