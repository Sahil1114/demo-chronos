package io.github.sahilmakkar.chronos.storage.converter

import androidx.room3.ColumnTypeConverter
import io.github.sahilmakkar.chronos.task.TaskState

/**
 * Room type converter for [TaskState] enum values.
 *
 * Stores lifecycle states (`ENQUEUED`, `RUNNING`, `SUCCEEDED`, etc.) as standard SQLite `TEXT`
 * strings and reconstitutes them upon reading rows from the database.
 */
internal class TaskStateConverter {

    /**
     * Converts a [TaskState] enum entry to its SQLite text representation.
     *
     * @param taskState The current lifecycle state of the task.
     * @return The canonical [Enum.name] string representation.
     */
    @ColumnTypeConverter
    fun fromTaskState(taskState: TaskState): String {
        return taskState.name
    }

    /**
     * Parses a stored SQLite string into a [TaskState] enum constant.
     *
     * @param taskState The raw string persisted in the database column.
     * @return The reconstituted [TaskState] enum constant.
     * @throws IllegalArgumentException if the stored string is unrecognized.
     */
    @ColumnTypeConverter
    fun toTaskState(taskState: String): TaskState {
        return TaskState.valueOf(value = taskState)
    }
}
