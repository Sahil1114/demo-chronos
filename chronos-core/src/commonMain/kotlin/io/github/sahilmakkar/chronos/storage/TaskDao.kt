package io.github.sahilmakkar.chronos.storage

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import io.github.sahilmakkar.chronos.task.TaskState
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object for tasks, tags, and dependencies tables.
 */
@Dao
internal interface TaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<TaskTagEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDependencies(dependencies: List<TaskDependencyEntity>)

    @androidx.room3.Transaction
    suspend fun insertChain(
        tasks: List<TaskEntity>,
        tags: List<TaskTagEntity>,
        dependencies: List<TaskDependencyEntity>
    ) {
        insertTasks(tasks)
        insertTags(tags)
        insertDependencies(dependencies)
    }

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTask(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE uniqueTaskName = :name")
    suspend fun getTaskByName(name: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE state = :state")
    suspend fun getTasksByState(state: TaskState): List<TaskEntity>

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasks(): List<TaskEntity>

    @Query("UPDATE tasks SET state = :state WHERE id = :id")
    suspend fun updateTaskState(id: String, state: TaskState)

    @Query("UPDATE tasks SET state = :running WHERE id = :id AND state IN (:claimable)")
    suspend fun claim(id: String, running: TaskState, claimable: List<TaskState>): Int

    @Query("UPDATE tasks SET state = 'CANCELLED' WHERE id = :id AND state NOT IN ('SUCCEEDED', 'FAILED', 'CANCELLED')")
    suspend fun cancelTaskIfActive(id: String): Int

    @Query("UPDATE tasks SET state = 'CANCELLED' WHERE state NOT IN ('SUCCEEDED', 'FAILED', 'CANCELLED')")
    suspend fun cancelAllActiveTasks(): Int

    @Query("UPDATE tasks SET state = :state, outputDataJson = :outputJson WHERE id = :id")
    suspend fun updateTaskStateAndOutput(id: String, state: TaskState, outputJson: String)

    @Query("UPDATE tasks SET progressDataJson = :progressJson WHERE id = :id")
    suspend fun updateTaskProgress(id: String, progressJson: String)

    @Query("UPDATE tasks SET runAttemptCount = :count WHERE id = :id")
    suspend fun updateRunAttemptCount(id: String, count: Int)

    @Query("UPDATE tasks SET scheduledAt = :scheduledAt WHERE id = :id")
    suspend fun updateScheduledAt(id: String, scheduledAt: Long)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTask(id: String)

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()

    @Query("SELECT tag FROM task_tags WHERE taskId = :taskId")
    suspend fun getTagsForTask(taskId: String): List<String>

    @Query("SELECT taskId FROM task_tags WHERE tag = :tag")
    suspend fun getTaskIdsByTag(tag: String): List<String>

    @Query("SELECT * FROM task_dependencies WHERE parentTaskId = :parentTaskId")
    suspend fun getDependenciesForParent(parentTaskId: String): List<TaskDependencyEntity>

    @Query("SELECT * FROM task_dependencies WHERE childTaskId = :childTaskId")
    suspend fun getDependenciesForChild(childTaskId: String): List<TaskDependencyEntity>

    @Query("SELECT * FROM task_dependencies")
    suspend fun getAllDependencies(): List<TaskDependencyEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeTask(id: String): Flow<TaskEntity?>

    @Query("SELECT MIN(scheduledAt) FROM tasks WHERE state IN (:states)")
    fun observeNextDueAt(states: List<TaskState>): Flow<Long?>

    @Query("SELECT * FROM tasks WHERE state IN (:states) ORDER BY scheduledAt ASC LIMIT 1")
    suspend fun getNextDueTask(states: List<TaskState>): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id IN (SELECT taskId FROM task_tags WHERE tag = :tag)")
    fun observeTasksByTag(tag: String): Flow<List<TaskEntity>>
}
