package io.github.sahilmakkar.chronos.storage

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import io.github.sahilmakkar.chronos.task.TaskState
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object (DAO) providing persistent database operations for tasks, tags, and execution chains.
 *
 * Implements atomic task claiming, reactive status observation flows, batch chain insertions,
 * and constraint evaluation queries.
 */
@Dao
internal interface TaskDao {

    /**
     * Inserts or replaces an individual task record in the `tasks` table.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    /**
     * Inserts or replaces a batch of task records.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    /**
     * Inserts or replaces a batch of task tags.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<TaskTagEntity>)

    /**
     * Inserts or replaces a batch of inter-task dependency edges.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDependencies(dependencies: List<TaskDependencyEntity>)

    /**
     * Atomically inserts an entire task chain (tasks, tags, and dependencies) within a single transaction.
     */
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

    /**
     * Retrieves a task record by its unique identifier, or null if not found.
     */
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTask(id: String): TaskEntity?

    /**
     * Retrieves an active or stored task matching the specified unique name.
     */
    @Query("SELECT * FROM tasks WHERE uniqueTaskName = :name")
    suspend fun getTaskByName(name: String): TaskEntity?

    /**
     * Retrieves all tasks currently in the given [TaskState].
     */
    @Query("SELECT * FROM tasks WHERE state = :state")
    suspend fun getTasksByState(state: TaskState): List<TaskEntity>

    /**
     * Retrieves all task records stored in the database.
     */
    @Query("SELECT * FROM tasks")
    suspend fun getAllTasks(): List<TaskEntity>

    /**
     * Updates the lifecycle state of a task.
     */
    @Query("UPDATE tasks SET state = :state WHERE id = :id")
    suspend fun updateTaskState(id: String, state: TaskState)

    /**
     * Atomically claims an eligible task by transitioning it from [claimable] states to [running].
     *
     * @return Number of rows updated (1 if claimed successfully, 0 if already claimed or state changed).
     */
    @Query("UPDATE tasks SET state = :running WHERE id = :id AND state IN (:claimable)")
    suspend fun claim(id: String, running: TaskState, claimable: List<TaskState>): Int

    /**
     * Transitions an active task to CANCELLED only if it is not already in a terminal state.
     *
     * @return Number of rows updated (1 if cancelled, 0 if already terminal).
     */
    @Query("UPDATE tasks SET state = 'CANCELLED' WHERE id = :id AND state NOT IN ('SUCCEEDED', 'FAILED', 'CANCELLED')")
    suspend fun cancelTaskIfActive(id: String): Int

    /**
     * Transitions all non-terminal tasks to CANCELLED in a single query.
     *
     * @return Number of tasks cancelled.
     */
    @Query("UPDATE tasks SET state = 'CANCELLED' WHERE state NOT IN ('SUCCEEDED', 'FAILED', 'CANCELLED')")
    suspend fun cancelAllActiveTasks(): Int

    /**
     * Updates a task's state and stores its terminal or failure output payload JSON.
     */
    @Query("UPDATE tasks SET state = :state, outputDataJson = :outputJson WHERE id = :id")
    suspend fun updateTaskStateAndOutput(id: String, state: TaskState, outputJson: String)

    /**
     * Updates the live progress payload JSON for a running task.
     */
    @Query("UPDATE tasks SET progressDataJson = :progressJson WHERE id = :id")
    suspend fun updateTaskProgress(id: String, progressJson: String)

    /**
     * Updates the completed run attempt counter for a task.
     */
    @Query("UPDATE tasks SET runAttemptCount = :count WHERE id = :id")
    suspend fun updateRunAttemptCount(id: String, count: Int)

    /**
     * Updates the scheduled execution epoch millisecond timestamp for a task.
     */
    @Query("UPDATE tasks SET scheduledAt = :scheduledAt WHERE id = :id")
    suspend fun updateScheduledAt(id: String, scheduledAt: Long)

    /**
     * Deletes a task record from the database.
     */
    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTask(id: String)

    /**
     * Deletes all task records from the database.
     */
    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()

    /**
     * Retrieves all tag strings associated with the given task ID.
     */
    @Query("SELECT tag FROM task_tags WHERE taskId = :taskId")
    suspend fun getTagsForTask(taskId: String): List<String>

    /**
     * Retrieves all task IDs tagged with the given tag string.
     */
    @Query("SELECT taskId FROM task_tags WHERE tag = :tag")
    suspend fun getTaskIdsByTag(tag: String): List<String>

    /**
     * Retrieves all dependency records where the specified task acts as the parent.
     */
    @Query("SELECT * FROM task_dependencies WHERE parentTaskId = :parentTaskId")
    suspend fun getDependenciesForParent(parentTaskId: String): List<TaskDependencyEntity>

    /**
     * Retrieves all dependency records where the specified task acts as the child.
     */
    @Query("SELECT * FROM task_dependencies WHERE childTaskId = :childTaskId")
    suspend fun getDependenciesForChild(childTaskId: String): List<TaskDependencyEntity>

    /**
     * Retrieves all dependency records across all chains.
     */
    @Query("SELECT * FROM task_dependencies")
    suspend fun getAllDependencies(): List<TaskDependencyEntity>

    /**
     * Observes real-time updates for a single task record by ID.
     */
    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeTask(id: String): Flow<TaskEntity?>

    /**
     * Observes the earliest scheduledAt timestamp among tasks in [states].
     */
    @Query("SELECT MIN(scheduledAt) FROM tasks WHERE state IN (:states)")
    fun observeNextDueAt(states: List<TaskState>): Flow<Long?>

    /**
     * Retrieves the single earliest due task in any of [states].
     */
    @Query("SELECT * FROM tasks WHERE state IN (:states) ORDER BY scheduledAt ASC LIMIT 1")
    suspend fun getNextDueTask(states: List<TaskState>): TaskEntity?

    /**
     * Observes real-time updates for all tasks tagged with [tag].
     */
    @Query("SELECT * FROM tasks WHERE id IN (SELECT taskId FROM task_tags WHERE tag = :tag)")
    fun observeTasksByTag(tag: String): Flow<List<TaskEntity>>
}
