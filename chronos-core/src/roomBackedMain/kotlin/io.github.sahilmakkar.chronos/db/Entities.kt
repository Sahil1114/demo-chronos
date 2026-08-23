package io.github.sahilmakkar.chronos.db

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import io.github.sahilmakkar.chronos.task.policy.DependentTaskPolicy

/**
 * Database entity representing a scheduled background task request.
 * Contains configuration, execution state, output payload, and scheduling constraints.
 */
@Entity(
    tableName = "work_requests",
    indices = [Index(value = ["uniqueWorkName"])] // removed unique = true to support APPEND chains
)
data class WorkRequestEntity(
    @PrimaryKey val id: String,
    val workerClassName: String,
    val state: String,
    val uniqueWorkName: String?,
    val maxRetries: Int,
    val runAttemptCount: Int,
    val timeoutMs: Long?,
    val scheduledAt: Long,
    val chainId: String?,
    val chainOrder: Int,
    val inputDataJson: String,
    val outputDataJson: String,
    val progressDataJson: String,
    val backoffPolicy: String,
    val backoffDelayMs: Long,
    
    // Constraints
    val networkType: String,
    val requiresCharging: Boolean,
    val requiresBatteryNotLow: Boolean,
    val requiresStorageNotLow: Boolean,
    val requiresDeviceIdle: Boolean,
    val windowStartHour: Int?,
    val windowEndHour: Int?,
    val windowTimeZone: String?,
    
    // Periodic
    val repeatIntervalMs: Long?
)

/**
 * Database entity representing a tag associated with a specific task request.
 * Allows querying or cancelling groups of tasks using a tag query.
 *
 * @property workRequestId ID of the associated task request.
 * @property tag String label tag.
 */
@Entity(
    tableName = "work_tags",
    primaryKeys = ["workRequestId", "tag"],
    foreignKeys = [
        ForeignKey(
            entity = WorkRequestEntity::class,
            parentColumns = ["id"],
            childColumns = ["workRequestId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tag"]), Index(value = ["workRequestId"])]
)
data class WorkTagEntity(
    val workRequestId: String,
    val tag: String
)

/**
 * Database entity representing a dependency relationship between a parent task and a child task.
 * Used to model execution chains where the child can only execute after parent completes.
 *
 * @property parentWorkId ID of the parent task that must finish first.
 * @property childWorkId ID of the dependent child task.
 * @property chainFailurePolicy Strategy policy describing how parent failures affect this child.
 * @property parentOutputMergeStrategy Merging strategy to merge parent output into child input.
 */
@Entity(
    tableName = "work_dependencies",
    primaryKeys = ["parentWorkId", "childWorkId"],
    foreignKeys = [
        ForeignKey(
            entity = WorkRequestEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentWorkId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WorkRequestEntity::class,
            parentColumns = ["id"],
            childColumns = ["childWorkId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["parentWorkId"]), Index(value = ["childWorkId"])]
)
data class WorkDependencyEntity(
    val parentWorkId: String,
    val childWorkId: String,
    val chainFailurePolicy: DependentTaskPolicy,
    val parentOutputMergeStrategy: String = "MERGE"
)
