package io.github.sahilmakkar.chronos.roomDb

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import io.github.sahilmakkar.chronos.task.TaskState
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkRequest(workRequest: WorkRequestEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<WorkTagEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDependencies(dependencies: List<WorkDependencyEntity>)

    @Query("SELECT * FROM work_requests WHERE id = :id")
    suspend fun getWorkRequest(id: String): WorkRequestEntity?


    @Query("SELECT * FROM work_requests WHERE uniqueWorkName = :name")
    suspend fun getWorkRequestByName(name: String): WorkRequestEntity?


    @Query("SELECT * FROM work_requests WHERE state = :state")
    suspend fun getWorkRequestsByState(state: TaskState): List<WorkRequestEntity>

    @Query("SELECT * FROM work_requests")
    suspend fun getAllWorkRequests(): List<WorkRequestEntity>

    @Query("UPDATE work_requests SET state = :state WHERE id = :id")
    suspend fun updateWorkState(id: String, state: TaskState)

    @Query("UPDATE work_requests SET state = :state, outputDataJson = :outputJson WHERE id = :id")
    suspend fun updateWorkStateAndOutput(id: String, state: TaskState, outputJson: String)

    @Query("UPDATE work_requests SET progressDataJson = :progressJson WHERE id = :id")
    suspend fun updateWorkProgress(id: String, progressJson: String)

    @Query("UPDATE work_requests SET runAttemptCount = :count WHERE id = :id")
    suspend fun updateRunAttemptCount(id: String, count: Int)

    @Query("UPDATE work_requests SET scheduledAt = :scheduledAt WHERE id = :id")
    suspend fun updateScheduledAt(id: String, scheduledAt: Long)

    @Query("DELETE FROM work_requests WHERE id = :id")
    suspend fun deleteWorkRequest(id: String)

    @Query("DELETE FROM work_requests")
    suspend fun deleteAllWorkRequests()

    @Query("SELECT tag FROM work_tags WHERE workRequestId = :workRequestId")
    suspend fun getTagsForWorkRequest(workRequestId: String): List<String>

    @Query("SELECT workRequestId FROM work_tags WHERE tag = :tag")
    suspend fun getWorkRequestIdsByTag(tag: String): List<String>

    @Query("SELECT * FROM work_dependencies WHERE parentWorkId = :parentId")
    suspend fun getDependenciesForParent(parentId: String): List<WorkDependencyEntity>

    @Query("SELECT * FROM work_dependencies WHERE childWorkId = :childId")
    suspend fun getDependenciesForChild(childId: String): List<WorkDependencyEntity>

    @Query("SELECT * FROM work_dependencies")
    suspend fun getAllDependencies(): List<WorkDependencyEntity>

    @Query("SELECT * FROM work_requests WHERE id = :id")
    fun observeWorkRequest(id: String): Flow<WorkRequestEntity?>

    @Query("SELECT * FROM work_requests WHERE id IN (SELECT workRequestId FROM work_tags WHERE tag = :tag)")
    fun observeWorkRequestsByTag(tag: String): Flow<List<WorkRequestEntity>>
}
