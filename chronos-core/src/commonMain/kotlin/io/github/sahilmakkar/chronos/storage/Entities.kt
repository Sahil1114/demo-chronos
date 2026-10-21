package io.github.sahilmakkar.chronos.storage

import androidx.room3.ColumnTypeConverters
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import io.github.sahilmakkar.chronos.storage.converter.BackoffPolicyConverter
import io.github.sahilmakkar.chronos.storage.converter.DependentTaskPolicyConverter
import io.github.sahilmakkar.chronos.storage.converter.NetworkTypeConverter
import io.github.sahilmakkar.chronos.storage.converter.TaskStateConverter
import io.github.sahilmakkar.chronos.task.TaskState
import io.github.sahilmakkar.chronos.task.constraints.NetworkType
import io.github.sahilmakkar.chronos.task.policy.BackoffPolicy
import io.github.sahilmakkar.chronos.task.policy.DependentTaskPolicy

@Entity(
    tableName = "tasks",
    indices = [Index(value = ["uniqueTaskName"])]
)
@ColumnTypeConverters(
    TaskStateConverter::class,
    BackoffPolicyConverter::class,
    NetworkTypeConverter::class
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val workerClassName: String,
    val state: TaskState,
    val uniqueTaskName: String?,
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

    // Periodic (null for one-time)
    val repeatIntervalMs: Long? = null
)

@Entity(
    tableName = "task_tags",
    primaryKeys = ["taskId", "tag"],
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tag"]), Index(value = ["taskId"])]
)
data class TaskTagEntity(
    val taskId: String,
    val tag: String
)

@Entity(
    tableName = "task_dependencies",
    primaryKeys = ["parentTaskId", "childTaskId"],
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentTaskId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["childTaskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["parentTaskId"]), Index(value = ["childTaskId"])]
)
@ColumnTypeConverters(DependentTaskPolicyConverter::class)
data class TaskDependencyEntity(
    val parentTaskId: String,
    val childTaskId: String,
    val chainFailurePolicy: DependentTaskPolicy = DependentTaskPolicy.STOP_ON_FAILURE
)

@Deprecated("Use TaskEntity instead", ReplaceWith("TaskEntity"))
typealias WorkRequestEntity = TaskEntity

@Deprecated("Use TaskTagEntity instead", ReplaceWith("TaskTagEntity"))
typealias WorkTagEntity = TaskTagEntity

@Deprecated("Use TaskDependencyEntity instead", ReplaceWith("TaskDependencyEntity"))
typealias WorkDependencyEntity = TaskDependencyEntity
