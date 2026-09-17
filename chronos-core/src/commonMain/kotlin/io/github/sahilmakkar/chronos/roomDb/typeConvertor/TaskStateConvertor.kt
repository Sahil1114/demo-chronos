package io.github.sahilmakkar.chronos.roomDb.typeConvertor

import androidx.room3.TypeConverter
import io.github.sahilmakkar.chronos.task.TaskState

class TaskStateConvertor {
    @TypeConverter
    fun fromTaskState(taskState: TaskState): String {
        return  taskState.name
    }

    @TypeConverter
    fun toTaskState(taskState: String): TaskState {
        return TaskState.valueOf(taskState)
    }
}