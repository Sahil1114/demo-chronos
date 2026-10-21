package io.github.sahilmakkar.chronos.storage

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import io.github.sahilmakkar.chronos.task.TaskState
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<TaskTagEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDependencies(dependencies: List<TaskDependencyEntity>)

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

    @Query("SELECT * FROM tasks WHERE id IN (SELECT taskId FROM task_tags WHERE tag = :tag)")
    fun observeTasksByTag(tag: String): Flow<List<TaskEntity>>

    // Compatibility methods delegating to new names
    suspend fun insertWorkRequest(workRequest: TaskEntity) = insertTask(workRequest)
    suspend fun getWorkRequest(id: String): TaskEntity? = getTask(id)
    suspend fun getWorkRequestByName(name: String): TaskEntity? = getTaskByName(name)
    suspend fun getWorkRequestsByState(state: TaskState): List<TaskEntity> = getTasksByState(state)
    suspend fun getAllWorkRequests(): List<TaskEntity> = getAllTasks()
    suspend fun updateWorkState(id: String, state: TaskState) = updateTaskState(id, state)
    suspend fun updateWorkStateAndOutput(id: String, state: TaskState, outputJson: String) =
        updateTaskStateAndOutput(id, state, outputJson)
    suspend fun updateWorkProgress(id: String, progressJson: String) = updateTaskProgress(id, progressJson)
    suspend fun deleteWorkRequest(id: String) = deleteTask(id)
    suspend fun deleteAllWorkRequests() = deleteAllTasks()
    suspend fun getTagsForWorkRequest(workRequestId: String): List<String> = getTagsForTask(workRequestId)
    suspend fun getWorkRequestIdsByTag(tag: String): List<String> = getTaskIdsByTag(tag)
    fun observeWorkRequest(id: String): Flow<TaskEntity?> = observeTask(id)
    fun observeWorkRequestsByTag(tag: String): Flow<List<TaskEntity>> = observeTasksByTag(tag)
}

@Deprecated("Use TaskDao instead", ReplaceWith("TaskDao"))
typealias WorkDao = TaskDao
