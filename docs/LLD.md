# Chronos — Low-Level Design (LLD)

This document provides the definitive Low-Level Design (LLD) for Chronos, specifying the exact class contracts, interface signatures, persistence schemas, algorithms, platform delegates, and edge-case handling across all targets.

---

## 1. Package Structure & Source Sets

```
chronos-core/
├── src/commonMain/kotlin/io/github/sahilmakkar/chronos/
│   ├── Chronos.kt                          # Public library interface & companion extensions
│   ├── ChronosEngine.kt                    # Internal core implementation orchestrating all modules
│   ├── ChronosRegistry.kt                  # Thread-safe holder for the Chronos singleton
│   ├── PlatformSupport.kt                  # Expect platform initialization token
│   ├── Daemon.kt                           # Expect background poller / lifecycle coordinator
│   ├── network/
│   │   ├── NetworkChecker.kt               # Common network verification interface
│   │   └── NetworkType.kt                  # Network requirement enum
│   ├── scheduler/
│   │   ├── PlatformScheduler.kt            # On-demand execution trigger interface
│   │   ├── WorkObserver.kt                 # Flow observation interface
│   │   ├── workEnqueuer/
│   │   │   ├── WorkEnqueuer.kt             # Enqueue contract
│   │   │   └── ChronosWorkEnqueuer.kt      # Expect class for enqueuing
│   │   └── canceller/
│   │       ├── WorkCanceller.kt            # Cancellation contract
│   │       └── ChronosWorkCanceller.kt     # Expect class for cancelling
│   ├── task/
│   │   ├── TaskChain.kt                    # Fluent DAG task chain builder
│   │   ├── TaskResult.kt                   # Sealed result: Success, Failure, Retry
│   │   ├── TaskState.kt                    # Enum lifecycle states
│   │   ├── constraints/
│   │   │   ├── EnvironmentConstraints.kt   # Hardware/OS constraint criteria
│   │   │   └── TimeWindow.kt               # Execution hour window definition
│   │   ├── model/
│   │   │   ├── TaskData.kt                 # Key-value data container
│   │   │   ├── TaskDataJson.kt             # JSON serialization / deserialization helpers
│   │   │   └── TaskInfo.kt                 # Read-only task snapshot for UI observation
│   │   ├── policy/
│   │   │   ├── BackoffPolicy.kt            # LINEAR or EXPONENTIAL
│   │   │   ├── DependentTaskPolicy.kt      # STOP_ON_FAILURE or CONTINUE_ON_FAILURE
│   │   │   └── UniqueTaskPolicy.kt         # KEEP, REPLACE, or APPEND
│   │   └── request/
│   │       ├── TaskRequest.kt              # Sealed base request
│   │       ├── OneTimeTaskRequest.kt       # One-time execution request
│   │       ├── PeriodicTaskRequest.kt      # Recurring execution request
│   │       └── TaskRequestBuilder.kt       # Fluent request builders
│   ├── worker/
│   │   ├── Worker.kt                       # Abstract base worker class
│   │   └── WorkerRegistry.kt               # String -> Worker factory registry
│   ├── roomDb/
│   │   ├── Database.kt                     # Expect createRoomDatabase()
│   │   ├── WorkDatabase.kt                 # Room 3 Database declaration & Constructor
│   │   ├── Entities.kt                     # Room entities (requests, tags, dependencies)
│   │   ├── WorkDao.kt                      # Room DAO queries & Flow streams
│   │   ├── WorkRequestEntityMapper.kt      # TaskRequest <-> WorkRequestEntity
│   │   ├── WorkInfoEntityMapper.kt         # WorkRequestEntity -> TaskInfo
│   │   ├── executor/
│   │   │   ├── WorkChainCoordinator.kt     # Core brain: dependencies, orphans, retry, execution
│   │   │   └── WorkTaskExecutor.kt         # Coroutine execution for workers
│   │   └── typeConvertor/                  # Room TypeConverters for enums
│   └── executor/
│       └── TaskExecutor.kt                 # Expect WorkConstraintVerifier & TaskExecutor interface
│
├── src/androidMain/kotlin/io/github/sahilmakkar/chronos/
│   ├── Chronos.android.kt                  # Android initialization & factory
│   ├── PlatformSupport.android.kt          # Wraps Android Context
│   ├── AndroidDelegatingWorker.kt          # androidx.work.CoroutineWorker bridge
│   ├── AndroidNetworkChecker.kt            # ConnectivityManager network checker
│   ├── Daemon.android.kt                   # 1-second coroutine polling daemon
│   ├── executor/
│   │   ├── AndroidTaskExecutor.kt          # Dispatches execution to WorkManager
│   │   └── TaskExecutor.android.kt         # Actual WorkConstraintVerifier
│   ├── mapper/
│   │   ├── WorkConstraintsMapper.kt        # Maps EnvironmentConstraints -> WorkManager Constraints
│   │   ├── WorkDataMapper.kt               # Maps TaskData <-> WorkManager Data
│   │   ├── WorkInfoMapper.kt               # Maps WorkInfo -> TaskInfo
│   │   └── WorkRequestMapper.kt            # Request builders for WorkManager
│   ├── roomDb/Database.android.kt          # Android SQLite database builder
│   └── scheduler/
│       ├── workEnqueuer/ChronosWorkEnqueuer.android.kt
│       └── canceller/ChronosWorkCanceller.android.kt
│
├── src/appleMain/kotlin/io/github/sahilmakkar/chronos/
│   ├── Chronos.apple.kt                    # Apple initialization & factory
│   ├── PlatformSupport.apple.kt            # Apple platform support token
│   ├── Daemon.apple.kt                     # BGTaskScheduler registration & sweeps
│   ├── IosNetworkChecker.kt                # NWPathMonitor network checker
│   ├── executor/
│   │   ├── IosBackgroundTaskScheduler.kt   # Registers & submits BGAppRefreshTask
│   │   └── TaskExecutor.apple.kt           # Actual WorkConstraintVerifier (battery, storage, NW)
│   ├── roomDb/Database.apple.kt            # NSDocumentDirectory Room database builder
│   └── scheduler/
│       ├── workEnqueuer/ChronosWorkEnqueuer.apple.kt
│       └── canceller/ChronosWorkCanceller.apple.kt
│
└── src/jvmMain/kotlin/io/github/sahilmakkar/chronos/
    ├── Chronos.jvm.kt                      # JVM initialization & factory
    ├── PlatformSupport.jvm.kt              # JVM platform support token
    ├── Daemon.jvm.kt                       # 1-second coroutine polling daemon
    ├── JvmNetworkChecker.kt                # NetworkInterface checker
    ├── executor/TaskExecutor.jvm.kt        # Actual WorkConstraintVerifier
    ├── roomDb/Database.jvm.kt              # user.home Room database builder
    └── scheduler/
        ├── workEnqueuer/ChronosWorkEnqueuer.jvm.kt
        └── canceller/ChronosWorkCanceller.jvm.kt
```

---

## 2. Public API Specification

### 2.1 The `Chronos` Interface
Located at [`Chronos.kt`](../chronos-core/src/commonMain/kotlin/io/github/sahilmakkar/chronos/Chronos.kt):

```kotlin
interface Chronos :
    WorkEnqueuer,
    WorkCanceller,
    WorkObserver,
    PlatformScheduler,
    AutoCloseable {

    val registry: WorkerRegistry

    companion object
}

// Platform initializer
expect fun Chronos.Companion.initialize(
    support: PlatformSupport,
    registry: WorkerRegistry
): Chronos

// Global accessor
val Chronos.Companion.instance: Chronos
    get() = ChronosRegistry.instance

// Testing hooks
fun Chronos.Companion.shutdown()
fun Chronos.Companion.setForTesting(instance: Chronos)

// Fluent chain builder entry point
fun Chronos.beginWith(request: TaskRequest): TaskChain = TaskChain.start(request)
```

### 2.2 Work Enqueuing Contract: `WorkEnqueuer`
Located at [`WorkEnqueuer.kt`](../chronos-core/src/commonMain/kotlin/io/github/sahilmakkar/chronos/scheduler/workEnqueuer/WorkEnqueuer.kt):

```kotlin
interface WorkEnqueuer {
    fun enqueue(request: TaskRequest)
    fun enqueueUniqueWork(name: String, policy: UniqueTaskPolicy, request: OneTimeTaskRequest)
    fun enqueueUniquePeriodicWork(name: String, policy: UniqueTaskPolicy, request: PeriodicTaskRequest)
    fun enqueue(chain: TaskChain)
}
```

### 2.3 Work Cancellation Contract: `WorkCanceller`
Located at [`WorkCanceller.kt`](../chronos-core/src/commonMain/kotlin/io/github/sahilmakkar/chronos/scheduler/canceller/WorkCanceller.kt):

```kotlin
interface WorkCanceller {
    fun cancelWorkById(id: String)
    fun cancelWorkByTag(tag: String)
    fun cancelAllWork()
}
```

### 2.4 Work Observation Contract: `WorkObserver`
Located at [`WorkObserver.kt`](../chronos-core/src/commonMain/kotlin/io/github/sahilmakkar/chronos/scheduler/WorkObserver.kt):

```kotlin
interface WorkObserver {
    fun getWorkInfoFlow(id: String): Flow<TaskInfo?>
    fun getWorkInfosByTagFlow(tag: String): Flow<List<TaskInfo>>
}
```

---

## 3. Worker Model & WorkerRegistry

### 3.1 Base `Worker` Class
Located at [`Worker.kt`](../chronos-core/src/commonMain/kotlin/io/github/sahilmakkar/chronos/worker/Worker.kt):

```kotlin
abstract class Worker(val inputData: TaskData) {
    abstract suspend fun doWork(): TaskResult

    private var progressListener: (suspend (TaskData) -> Unit)? = null

    internal fun setProgressListener(listener: suspend (TaskData) -> Unit) {
        progressListener = listener
    }

    suspend fun setProgress(data: TaskData) {
        progressListener?.invoke(data)
    }
}
```

### 3.2 `WorkerRegistry`
Located at [`WorkerRegistry.kt`](../chronos-core/src/commonMain/kotlin/io/github/sahilmakkar/chronos/worker/WorkerRegistry.kt):
Provides reflection-free instantiation of workers by string name:

```kotlin
class WorkerRegistry {
    private val factories = mutableMapOf<String, (TaskData) -> Worker>()

    fun register(className: String, factory: (TaskData) -> Worker) {
        factories[className] = factory
    }

    fun createWorker(className: String, inputData: TaskData): Worker {
        val factory = factories[className] ?: throw IllegalArgumentException(
            "Worker class $className is not registered. Ensure it is annotated with @RegisterWorker or registered manually."
        )
        return factory(inputData)
    }
}
```

---

## 4. Task Domain Models & Constraints

### 4.1 `TaskResult`
Located at [`TaskResult.kt`](../chronos-core/src/commonMain/kotlin/io/github/sahilmakkar/chronos/task/TaskResult.kt):

```kotlin
sealed class TaskResult {
    data class Success(val outputData: TaskData = workDataOf()) : TaskResult()
    data class Failure(val outputData: TaskData = workDataOf()) : TaskResult()
    object Retry : TaskResult()
}
```

### 4.2 `TaskState` Lifecycle
Located at [`TaskState.kt`](../chronos-core/src/commonMain/kotlin/io/github/sahilmakkar/chronos/task/TaskState.kt):

- `ENQUEUED`: Persisted in DB, waiting for scheduled time, dependencies, and constraints.
- `RUNNING`: Actively executing in a worker coroutine.
- `SUCCEEDED`: Terminal success; output stored in `outputDataJson`.
- `FAILED`: Terminal failure (explicit failure or max retries exhausted).
- `RETRYING`: Transient failure; rescheduled with computed backoff delay.
- `CANCELLED`: Terminal cancellation initiated by user or dependency failure.

### 4.3 `EnvironmentConstraints` & `TimeWindow`
Located at [`EnvironmentConstraints.kt`](../chronos-core/src/commonMain/kotlin/io/github/sahilmakkar/chronos/task/constraints/EnvironmentConstraints.kt) and [`TimeWindow.kt`](../chronos-core/src/commonMain/kotlin/io/github/sahilmakkar/chronos/task/constraints/TimeWindow.kt):

```kotlin
data class EnvironmentConstraints(
    val requiredNetworkType: NetworkType = NetworkType.NOT_REQUIRED,
    val requiresCharging: Boolean = false,
    val requiresBatteryNotLow: Boolean = false,
    val requiresStorageNotLow: Boolean = false,
    @BestEffortOnIOS("Device idle is not supported on iOS targets and behaves as a no-op.")
    @BestEffortOnJVM("Device idle is not supported on JVM targets and behaves as a no-op.")
    val requiresDeviceIdle: Boolean = false,
    val allowedTimeWindow: TimeWindow? = null
)

data class TimeWindow(
    val startHour: Int,    // 0..23
    val endHour: Int,      // 0..23
    val timeZone: String   // IANA timezone identifier, e.g. "UTC" or "America/New_York"
)
```

---

## 5. Persistence Layer (Room 3 KMP)

### 5.1 `WorkDatabase` & Entities
Located at [`WorkDatabase.kt`](../chronos-core/src/commonMain/kotlin/io/github/sahilmakkar/chronos/roomDb/WorkDatabase.kt) and [`Entities.kt`](../chronos-core/src/commonMain/kotlin/io/github/sahilmakkar/chronos/roomDb/Entities.kt):

#### 1. `WorkRequestEntity` (Table: `work_requests`)
```kotlin
@Entity(
    tableName = "work_requests",
    indices = [Index(value = ["uniqueWorkName"])]
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
    val networkType: NetworkType,
    val requiresCharging: Boolean,
    val requiresBatteryNotLow: Boolean,
    val requiresStorageNotLow: Boolean,
    val requiresDeviceIdle: Boolean,
    val windowStartHour: Int?,
    val windowEndHour: Int?,
    val windowTimeZone: String?,
    val repeatIntervalMs: Long?
)
```

#### 2. `WorkTagEntity` (Table: `work_tags`)
```kotlin
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
```

#### 3. `WorkDependencyEntity` (Table: `work_dependencies`)
```kotlin
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
```

### 5.2 `WorkDao` Interface
Located at [`WorkDao.kt`](../chronos-core/src/commonMain/kotlin/io/github/sahilmakkar/chronos/roomDb/WorkDao.kt):

```kotlin
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
```

---

## 6. Execution Coordination: `WorkChainCoordinator`

Located at [`WorkChainCoordinator.kt`](../chronos-core/src/commonMain/kotlin/io/github/sahilmakkar/chronos/roomDb/executor/WorkChainCoordinator.kt):

### 6.1 Orphan Sweep (`sweepOrphans`)
Recovers from unexpected process termination (crashes, system kills):
```kotlin
suspend fun sweepOrphans() {
    db.workDao().getWorkRequestsByState(TaskState.RUNNING).forEach {
        db.workDao().updateWorkState(id = it.id, state = TaskState.ENQUEUED)
    }
}
```

### 6.2 DAG Chain Recovery Sweep (`sweepChainRecovery`)
Evaluates all dependency edges:
```kotlin
suspend fun sweepChainRecovery() {
    db.workDao().getAllDependencies().forEach { dep ->
        val parent = db.workDao().getWorkRequest(id = dep.parentWorkId) ?: return@forEach
        val child = db.workDao().getWorkRequest(id = dep.childWorkId) ?: return@forEach
        when {
            parent.state == TaskState.SUCCEEDED && child.state == TaskState.ENQUEUED ->
                db.workDao().updateScheduledAt(
                    id = child.id,
                    scheduledAt = Clock.System.now().toEpochMilliseconds()
                )

            parent.state == TaskState.FAILED && child.state == TaskState.ENQUEUED ->
                if (dep.chainFailurePolicy == DependentTaskPolicy.STOP_ON_FAILURE) {
                    db.workDao().updateWorkState(id = child.id, state = TaskState.FAILED)
                } else {
                    db.workDao().updateScheduledAt(
                        id = child.id,
                        scheduledAt = Clock.System.now().toEpochMilliseconds()
                    )
                }
        }
    }
}
```

### 6.3 Pending Task Execution (`runPendingTasks`)
```kotlin
suspend fun runPendingTasks() {
    val nowMs = Clock.System.now().toEpochMilliseconds()
    val pending = db.workDao().getWorkRequestsByState(TaskState.ENQUEUED) +
            db.workDao().getWorkRequestsByState(TaskState.RETRYING)

    for (entity in pending) {
        if (entity.scheduledAt > nowMs) continue
        if (!resolveDependencies(entity = entity)) continue
        if (!constraintProvider.isSatisfied(entity)) continue

        val inputData = TaskData.fromJson(entity.inputDataJson)
        val result = taskExecutor.execute(entity, inputData)
        handleResult(entity, result)
    }
}
```

### 6.4 Result Handling & Backoff Calculation
```kotlin
private suspend fun handleResult(entity: WorkRequestEntity, result: TaskResult) {
    when (result) {
        is TaskResult.Success -> {
            db.workDao().updateWorkStateAndOutput(
                id = entity.id,
                state = TaskState.SUCCEEDED,
                outputJson = result.outputData.toJson()
            )
            if (entity.repeatIntervalMs != null && entity.repeatIntervalMs > 0) {
                db.workDao().updateScheduledAt(
                    id = entity.id,
                    scheduledAt = Clock.System.now().toEpochMilliseconds() + entity.repeatIntervalMs
                )
                db.workDao().updateWorkState(id = entity.id, state = TaskState.ENQUEUED)
                db.workDao().updateRunAttemptCount(id = entity.id, count = 0)
            }
            sweepChainRecovery()
            runPendingTasks()
        }

        is TaskResult.Failure -> {
            db.workDao().updateWorkStateAndOutput(
                id = entity.id,
                state = TaskState.FAILED,
                outputJson = result.outputData.toJson()
            )
            sweepChainRecovery()
            runPendingTasks()
        }

        TaskResult.Retry -> {
            val attempt = entity.runAttemptCount + 1
            if (attempt >= entity.maxRetries) {
                db.workDao().updateWorkStateAndOutput(
                    id = entity.id,
                    state = TaskState.FAILED,
                    outputJson = workDataOf("error" to "Max retries (${entity.maxRetries}) exhausted").toJson()
                )
                sweepChainRecovery()
                runPendingTasks()
            } else {
                db.workDao().updateScheduledAt(
                    id = entity.id,
                    scheduledAt = Clock.System.now().toEpochMilliseconds() + computeBackoffDelay(entity, attempt)
                )
                db.workDao().updateWorkState(id = entity.id, state = TaskState.RETRYING)
            }
        }
    }
}
```

---

## 7. Platform Executors & Workers

### 7.1 Apple / JVM: `WorkTaskExecutor`
Located at [`WorkTaskExecutor.kt`](../chronos-core/src/commonMain/kotlin/io/github/sahilmakkar/chronos/roomDb/executor/WorkTaskExecutor.kt):
Executes worker coroutines directly, enforces timeout, and updates Room DB with progress:
```kotlin
class WorkTaskExecutor(
    private val db: WorkDatabase,
    private val registry: WorkerRegistry
) : TaskExecutor {
    override suspend fun execute(entity: WorkRequestEntity, inputData: TaskData): TaskResult {
        db.workDao().updateWorkState(id = entity.id, state = TaskState.RUNNING)
        db.workDao().updateRunAttemptCount(id = entity.id, count = entity.runAttemptCount + 1)

        val worker = try {
            registry.createWorker(className = entity.workerClassName, inputData = inputData)
        } catch (e: Exception) {
            db.workDao().updateWorkState(id = entity.id, state = TaskState.FAILED)
            return TaskResult.Failure(outputData = workDataOf("error" to "Worker not registered: ${entity.workerClassName}"))
        }

        worker.setProgressListener { progress ->
            db.workDao().updateWorkProgress(id = entity.id, progressJson = progress.toJson())
        }

        return try {
            entity.timeoutMs?.takeIf { it > 0 }
                ?.let { timeout -> withTimeout(timeout) { worker.doWork() } }
                ?: worker.doWork()
        } catch (e: Exception) {
            TaskResult.Failure(outputData = workDataOf("error" to (e.message ?: "Execution failed")))
        }
    }
}
```

### 7.2 Android: `AndroidTaskExecutor` & `AndroidDelegatingWorker`
Located at [`AndroidTaskExecutor.kt`](../chronos-core/src/androidMain/kotlin/io/github/sahilmakkar/chronos/executor/AndroidTaskExecutor.kt) and [`AndroidDelegatingWorker.kt`](../chronos-core/src/androidMain/kotlin/io/github/sahilmakkar/chronos/AndroidDelegatingWorker.kt):
- Android does **not** allow WorkManager to handle retries (`no setBackoffCriteria`), guaranteeing that `WorkChainCoordinator` remains the single authoritative retry brain.
- When `AndroidDelegatingWorker` returns `Result.retry()`, `AndroidTaskExecutor` maps it to `TaskResult.Retry`.

---

## 8. KSP Annotation Processor (`chronos-processor`)

Located at [`WorkerProcessor.kt`](../chronos-processor/src/main/kotlin/io/github/sahilmakkar/chronos/processor/WorkerProcessor.kt):
- Scans `Resolver` for `@RegisterWorker`.
- Checks for custom `factory` parameter.
- Outputs `io.github.sahilmakkar.chronos.GeneratedWorkers.kt` containing:
  ```kotlin
  package io.github.sahilmakkar.chronos
  
  fun WorkerRegistry.registerGeneratedWorkers() {
      register("com.example.WorkerA") { WorkerA(it) }
  }
  ```

---

## 9. Error Handling & Edge Cases

| Scenario | Behavior / Mitigation |
|---|---|
| **App crash while worker is `RUNNING`** | On next startup, `Daemon` invokes `WorkChainCoordinator.sweepOrphans()`, moving all `RUNNING` tasks back to `ENQUEUED`. |
| **Parent task fails in a Chain** | `resolveDependencies()` and `sweepChainRecovery()` check `DependentTaskPolicy`. If `STOP_ON_FAILURE`, downstream tasks are marked `FAILED`. If `CONTINUE_ON_FAILURE`, child tasks run. |
| **Worker class name not registered** | `WorkTaskExecutor` catches exception, marks task `FAILED`, and records an explanatory error message in `outputDataJson`. |
| **Task execution exceeds `timeout`** | Wrapped with `withTimeout(timeoutMs)`. If timed out, `TimeoutCancellationException` causes `TaskResult.Failure("Execution failed")`. |
| **Max retries reached** | When `runAttemptCount + 1 >= maxRetries`, `handleResult()` marks state as `FAILED` instead of scheduling another backoff. |
| **iOS Background task expires** | `IosBackgroundTaskScheduler` invokes `task.expirationHandler`, immediately cancels the coroutine, runs `sweepOrphans()`, and sets `setTaskCompletedWithSuccess(false)`. |
| **Overnight time windows** | `WorkConstraintVerifier` supports windows crossing midnight (e.g. 22:00 to 06:00) using `hour >= start || hour < end`. |
