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

/**
 * Room entity representing an enqueued, active, or completed background task.
 *
 * @property id Canonical 36-character dashed UUID primary key.
 * @property workerClassName Fully-qualified class name of the target [io.github.sahilmakkar.chronos.worker.TaskWorker].
 * @property state Current execution state of the task.
 * @property uniqueTaskName Optional unique name for conflict resolution.
 * @property maxRetries Maximum number of retry attempts allowed after an initial failure.
 * @property runAttemptCount Number of execution attempts completed so far.
 * @property timeoutMs Maximum execution duration in milliseconds before timing out, or null.
 * @property scheduledAt Epoch millisecond timestamp when the task becomes eligible to run.
 * @property chainId Root task ID of the execution chain, or null if independent.
 * @property chainOrder Zero-indexed sequential position within an execution chain.
 * @property inputDataJson Serialized JSON representation of input parameters.
 * @property outputDataJson Serialized JSON representation of terminal output or failure diagnostics.
 * @property progressDataJson Serialized JSON representation of intermediate progress updates.
 * @property backoffPolicy Delay backoff algorithm for retry scheduling (LINEAR or EXPONENTIAL).
 * @property backoffDelayMs Base duration unit for retry backoff calculations in milliseconds.
 * @property networkType Required network connectivity state.
 * @property requiresCharging Whether device must be connected to power.
 * @property requiresBatteryNotLow Whether battery level must not be low.
 * @property requiresStorageNotLow Whether device storage must not be critically low.
 * @property requiresDeviceIdle Whether device must be idle.
 * @property windowStartHour Beginning hour of allowed time-of-day execution window (0..23), or null.
 * @property windowEndHour Ending hour of allowed time-of-day execution window (0..24), or null.
 * @property windowTimeZone Timezone string for time window calculations, or null.
 * @property repeatIntervalMs Recurrence interval in milliseconds for periodic tasks, or null for one-time tasks.
 */
@Entity(
    tableName = "tasks",
    indices = [Index(value = ["uniqueTaskName"])]
)
@ColumnTypeConverters(
    TaskStateConverter::class,
    BackoffPolicyConverter::class,
    NetworkTypeConverter::class
)
internal data class TaskEntity(
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

/**
 * Room entity mapping categorization tags to tasks for group queries and bulk cancellation.
 *
 * Cascading foreign key deletes automatically prune associated tag entries when a task is deleted.
 */
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
internal data class TaskTagEntity(
    val taskId: String,
    val tag: String
)

/**
 * Room entity representing parent-child dependency edges within a [io.github.sahilmakkar.chronos.task.TaskChain].
 *
 * @property parentTaskId The ID of the preceding task that must succeed before the child runs.
 * @property childTaskId The ID of the dependent task waiting for parent completion.
 * @property chainFailurePolicy Action to take if the parent task reaches terminal failure.
 */
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
internal data class TaskDependencyEntity(
    val parentTaskId: String,
    val childTaskId: String,
    val chainFailurePolicy: DependentTaskPolicy = DependentTaskPolicy.STOP_ON_FAILURE
)
