# Chronos Core (`chronos-core`)

[![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin-Multiplatform-blue.svg?logo=kotlin)](https://kotlinlang.org/docs/multiplatform.html)
[![Android](https://img.shields.io/badge/Platform-Android-green.svg?logo=android)](https://developer.android.com/)
[![iOS](https://img.shields.io/badge/Platform-iOS-lightgrey.svg?logo=apple)](https://developer.apple.com/ios/)
[![Desktop](https://img.shields.io/badge/Platform-JVM%20Desktop-orange.svg?logo=openjdk)](https://openjdk.org/)

**Chronos Core** is a robust, persistence-backed background task scheduling and execution library for **Kotlin Multiplatform (KMP)** supporting **Android**, **iOS (Apple)**, and **JVM Desktop**.

Modeled after modern declarative work scheduling principles, Chronos gives you a unified, type-safe API for one-time, periodic, unique, and chained tasks while ensuring persistence across app launches, device reboots, and operating system process deaths.

---

## Features

- 🔄 **Unified API**: Write your background workers and scheduling once in `commonMain`.
- 💾 **Authoritative Room 3 Persistence**: Powered by AndroidX Room 3 KMP with SQLite driver. Guarantees persistence of task queues, states, attempts, and dependencies.
- ⚡ **Self-Healing Coordination**:
  - **Orphan Sweeper**: Automatically recovers running tasks interrupted by app crashes or process kills.
  - **DAG Chain Recovery**: Cascades state transitions across task chains upon parent success/failure.
- 🔗 **Task Chaining (DAG)**: Sequence tasks (`A -> B -> C`) with customizable failure policies (`STOP_ON_FAILURE` vs `CONTINUE_ON_FAILURE`).
- ⏱️ **Flexible Retries & Backoff**: Exponential or linear backoff retry schedules with maximum retry limits.
- 🎯 **Unique Work Policies**: Prevent duplicate background work using `KEEP`, `REPLACE`, or `APPEND`.
- 📡 **Hardware & Environmental Constraints**:
  - Network constraints (`CONNECTED`, `UNMETERED`).
  - Battery constraints (`requiresCharging`, `requiresBatteryNotLow`).
  - Storage constraint (`requiresStorageNotLow`).
  - Time window constraint (`TimeWindow` with timezone support and overnight ranges).
- 👁️ **Reactive Observability**: Observe task status, live progress, and outputs in real-time via Kotlin Coroutines `Flow<TaskInfo?>`.

---

## Platform Execution Matrix

| Subsystem | Android | iOS / Apple | JVM Desktop |
| :--- | :--- | :--- | :--- |
| **Persistence** | Room 3 KMP (`SQLite`) | Room 3 KMP (`SQLite`) | Room 3 KMP (`SQLite`) |
| **Task Execution** | Dispatched to native Android `WorkManager` | Coroutine worker execution | Coroutine worker execution |
| **Background Trigger** | `WorkManager` & 1s Daemon Loop | Apple `BGTaskScheduler` (`BGAppRefreshTask`) | 1s Coroutine Polling Daemon |
| **Network Checker** | `ConnectivityManager` | `NWPathMonitor` | Java `NetworkInterface` |

---

## Getting Started

### 1. Initialization

Initialize the `Chronos` singleton during your application startup:

#### Android
```kotlin
// In your Application class or Jetpack Startup Initializer:
val registry = WorkerRegistry().apply {
    register("com.example.SyncWorker") { inputData -> SyncWorker(inputData) }
}
Chronos.initialize(PlatformSupport(context = applicationContext), registry)
```

#### Apple (iOS)
```kotlin
// In Swift AppDelegate / iOS Application lifecycle:
val registry = WorkerRegistry().apply {
    register("com.example.SyncWorker") { inputData -> SyncWorker(inputData) }
}
Chronos.initialize(PlatformSupport, registry)
```

#### JVM Desktop
```kotlin
val registry = WorkerRegistry().apply {
    register("com.example.SyncWorker") { inputData -> SyncWorker(inputData) }
}
Chronos.initialize(PlatformSupport, registry)
```

> **Tip:** You can also use the compile-time `chronos-processor` (KSP) to automatically generate worker registrations via `@RegisterWorker`.

---

### 2. Implementing a Worker

Extend `Worker` and implement the `suspend fun doWork()` function:

```kotlin
import io.github.sahilmakkar.chronos.worker.Worker
import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.workDataOf

class SyncWorker(inputData: TaskData) : Worker(inputData) {

    override suspend fun doWork(): TaskResult {
        val syncType = inputData.getString("sync_type") ?: "incremental"

        // Report live progress to observers
        setProgress(workDataOf("percent" to 25))

        return try {
            // Perform background work...
            setProgress(workDataOf("percent" to 100))

            TaskResult.Success(
                outputData = workDataOf("synced_items" to 42)
            )
        } catch (e: TransientNetworkException) {
            // Trigger retry with backoff
            TaskResult.Retry
        } catch (e: Exception) {
            // Mark permanent failure
            TaskResult.Failure(
                outputData = workDataOf("error" to (e.message ?: "Unknown error"))
            )
        }
    }
}
```

---

### 3. Scheduling Work

#### One-Time Task Request
```kotlin
import io.github.sahilmakkar.chronos.Chronos
import io.github.sahilmakkar.chronos.instance
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequestBuilder
import io.github.sahilmakkar.chronos.task.constraints.EnvironmentConstraints
import io.github.sahilmakkar.chronos.network.NetworkType
import io.github.sahilmakkar.chronos.task.policy.BackoffPolicy
import io.github.sahilmakkar.chronos.task.model.workDataOf
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.minutes

val request = OneTimeTaskRequestBuilder<SyncWorker>()
    .setInputData(workDataOf("sync_type" to "full"))
    .setInitialDelay(10.seconds)
    .setTimeout(5.minutes)
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15.seconds)
    .setMaxRetries(3)
    .setConstraints(
        EnvironmentConstraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresCharging(true)
            .build()
    )
    .addTag("sync_jobs")
    .build()

Chronos.instance.enqueue(request)
```

#### Unique Work
Avoid redundant duplicate tasks using unique work policies:
```kotlin
import io.github.sahilmakkar.chronos.task.policy.UniqueTaskPolicy

Chronos.instance.enqueueUniqueWork(
    name = "nightly_data_sync",
    policy = UniqueTaskPolicy.KEEP, // Options: KEEP, REPLACE, APPEND
    request = request
)
```

#### Periodic Task Request
```kotlin
import io.github.sahilmakkar.chronos.task.request.PeriodicTaskRequestBuilder
import kotlin.time.Duration.Companion.hours

val periodicRequest = PeriodicTaskRequestBuilder<SyncWorker>(repeatInterval = 1.hours)
    .setInputData(workDataOf("sync_type" to "hourly"))
    .build()

Chronos.instance.enqueueUniquePeriodicWork(
    name = "hourly_sync",
    policy = UniqueTaskPolicy.REPLACE,
    request = periodicRequest
)
```

#### Task Chaining (DAG)
Sequence interdependent operations:
```kotlin
import io.github.sahilmakkar.chronos.beginWith
import io.github.sahilmakkar.chronos.task.policy.DependentTaskPolicy

val fetchTask = OneTimeTaskRequestBuilder<FetchWorker>().build()
val processTask = OneTimeTaskRequestBuilder<ProcessWorker>().build()
val uploadTask = OneTimeTaskRequestBuilder<UploadWorker>().build()

Chronos.instance
    .beginWith(fetchTask)
    .then(processTask, onFailure = DependentTaskPolicy.STOP_ON_FAILURE)
    .then(uploadTask, onFailure = DependentTaskPolicy.STOP_ON_FAILURE)
    .let { chain -> Chronos.instance.enqueue(chain) }
```

---

### 4. Observing Work Status & Progress

Observe task progress and terminal states reactively using Kotlin Coroutines `Flow`:

```kotlin
coroutineScope.launch {
    Chronos.instance.getWorkInfoFlow(request.id).collect { info ->
        when (info?.state) {
            TaskState.ENQUEUED -> println("Waiting for constraints...")
            TaskState.RUNNING  -> {
                val percent = info.progress.getInt("percent", 0)
                println("Running: $percent%")
            }
            TaskState.SUCCEEDED -> println("Completed! Output: ${info.outputData.toMap()}")
            TaskState.FAILED    -> println("Failed on attempt ${info.runAttemptCount}")
            TaskState.RETRYING  -> println("Retrying...")
            TaskState.CANCELLED -> println("Cancelled")
            null                -> println("Task not found")
        }
    }
}
```

Observe all tasks matching a specific tag:
```kotlin
Chronos.instance.getWorkInfosByTagFlow("sync_jobs").collect { tasks ->
    println("Active sync jobs: ${tasks.size}")
}
```

---

### 5. Cancelling Work

```kotlin
// Cancel by ID
Chronos.instance.cancelWorkById(request.id)

// Cancel all tasks matching a tag
Chronos.instance.cancelWorkByTag("sync_jobs")

// Cancel all scheduled and active work
Chronos.instance.cancelAllWork()
```

---

## Architectural Documentation

For comprehensive design documents and implementation deep dives, refer to the [`docs/`](../docs) directory:
- [**High-Level Design & System Design (HLD & DFD)**](../docs/SYSTEM_DESIGN_HLD_DFD.md)
- [**Detailed HLD Deep Dives**](../docs/HLD_DEEP_DIVES.md)
- [**Low-Level Design (LLD)**](../docs/LLD.md)
- [**Technical Project Plan**](../docs/project_plan.md)
