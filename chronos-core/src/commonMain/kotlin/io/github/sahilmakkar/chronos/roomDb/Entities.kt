package io.github.sahilmakkar.chronos.roomDb

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.TypeConverters
import io.github.sahilmakkar.chronos.network.NetworkType
import io.github.sahilmakkar.chronos.roomDb.typeConvertor.BackoffPolicyConvertor
import io.github.sahilmakkar.chronos.roomDb.typeConvertor.DependentTaskPolicyConvertor
import io.github.sahilmakkar.chronos.roomDb.typeConvertor.NetworkTypeConvertor
import io.github.sahilmakkar.chronos.roomDb.typeConvertor.TaskStateConvertor
import io.github.sahilmakkar.chronos.task.TaskState
import io.github.sahilmakkar.chronos.task.policy.BackoffPolicy
import io.github.sahilmakkar.chronos.task.policy.DependentTaskPolicy


@Entity(
    tableName = "work_requests",
    indices = [Index(value = ["uniqueWorkName"])]
)
@TypeConverters(
    TaskStateConvertor::class,
    BackoffPolicyConvertor::class,
    NetworkTypeConvertor::class
)
data class WorkRequestEntity(
    @PrimaryKey val id: String,
    val workerClassName: String,
    val state: TaskState,
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
    val backoffPolicy: BackoffPolicy,
    val backoffDelayMs: Long,

    // Constraints
    val networkType: NetworkType,
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
@TypeConverters(DependentTaskPolicyConvertor::class)
data class WorkDependencyEntity(
    val parentWorkId: String,
    val childWorkId: String,
    val chainFailurePolicy: DependentTaskPolicy,
    val parentOutputMergeStrategy: String = "MERGE"
)
