package io.github.sahilmakkar.chronos.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import io.github.sahilmakkar.chronos.task.TaskState
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) providing Room database operations for managing [WorkRequestEntity]s,
 * [WorkTagEntity]s, and [WorkDependencyEntity]s.
 */
@Dao
interface WorkDao {
    /**
     * Inserts or replaces a task request entity in the database.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkRequest(workRequest: WorkRequestEntity)

    /**
     * Inserts tags associated with tasks.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<WorkTagEntity>)

    /**
     * Inserts execution dependency relations between parents and children.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDependencies(dependencies: List<WorkDependencyEntity>)

    /**
     * Finds a task request by its ID.
     */
    @Query("SELECT * FROM work_requests WHERE id = :id")
    suspend fun getWorkRequest(id: String): WorkRequestEntity?

    /**
     * Finds a unique task request by its name identifier.
     */
    @Query("SELECT * FROM work_requests WHERE uniqueWorkName = :name")
    suspend fun getWorkRequestByName(name: String): WorkRequestEntity?

    /**
     * Retrieves all tasks matching a specific execution state.
     */
    @Query("SELECT * FROM work_requests WHERE state = :state")
    suspend fun getWorkRequestsByState(state: TaskState): List<WorkRequestEntity>

    /**
     * Retrieves all tasks currently stored in the database.
     */
    @Query("SELECT * FROM work_requests")
    suspend fun getAllWorkRequests(): List<WorkRequestEntity>

    /**
     * Updates the execution state of a specific task.
     */
    @Query("UPDATE work_requests SET state = :state WHERE id = :id")
    suspend fun updateWorkState(id: String, state: TaskState)

    /**
     * Updates both the state and the final output JSON payload for a task.
     */
    @Query("UPDATE work_requests SET state = :state, outputDataJson = :outputJson WHERE id = :id")
    suspend fun updateWorkStateAndOutput(id: String, state: TaskState, outputJson: String)

    /**
     * Updates the intermediate progress JSON payload for a running task.
     */
    @Query("UPDATE work_requests SET progressDataJson = :progressJson WHERE id = :id")
    suspend fun updateWorkProgress(id: String, progressJson: String)

    /**
     * Updates the number of completed run attempts for a task.
     */
    @Query("UPDATE work_requests SET runAttemptCount = :count WHERE id = :id")
    suspend fun updateRunAttemptCount(id: String, count: Int)

    /**
     * Updates the next scheduled epoch millisecond execution timestamp for a task.
     */
    @Query("UPDATE work_requests SET scheduledAt = :scheduledAt WHERE id = :id")
    suspend fun updateScheduledAt(id: String, scheduledAt: Long)

    /**
     * Deletes a task request by its ID.
     */
    @Query("DELETE FROM work_requests WHERE id = :id")
    suspend fun deleteWorkRequest(id: String)

    /**
     * Deletes all task requests from the database.
     */
    @Query("DELETE FROM work_requests")
    suspend fun deleteAllWorkRequests()

    /**
     * Retrieves all string tags associated with a specific task ID.
     */
    @Query("SELECT tag FROM work_tags WHERE workRequestId = :workRequestId")
    suspend fun getTagsForWorkRequest(workRequestId: String): List<String>

    /**
     * Retrieves all task IDs tagged with the given string tag.
     */
    @Query("SELECT workRequestId FROM work_tags WHERE tag = :tag")
    suspend fun getWorkRequestIdsByTag(tag: String): List<String>

    /**
     * Retrieves all dependencies where the given task ID acts as the parent.
     */
    @Query("SELECT * FROM work_dependencies WHERE parentWorkId = :parentId")
    suspend fun getDependenciesForParent(parentId: String): List<WorkDependencyEntity>

    /**
     * Retrieves all dependencies where the given task ID acts as the child.
     */
    @Query("SELECT * FROM work_dependencies WHERE childWorkId = :childId")
    suspend fun getDependenciesForChild(childId: String): List<WorkDependencyEntity>

    /**
     * Retrieves all dependency relation mapping records.
     */
    @Query("SELECT * FROM work_dependencies")
    suspend fun getAllDependencies(): List<WorkDependencyEntity>

    /**
     * Returns a reactive Flow observing updates to a specific task.
     */
    @Query("SELECT * FROM work_requests WHERE id = :id")
    fun observeWorkRequest(id: String): Flow<WorkRequestEntity?>

    /**
     * Returns a reactive Flow observing updates to all tasks matching a tag.
     */
    @Query("SELECT * FROM work_requests WHERE id IN (SELECT workRequestId FROM work_tags WHERE tag = :tag)")
    fun observeWorkRequestsByTag(tag: String): Flow<List<WorkRequestEntity>>
}
