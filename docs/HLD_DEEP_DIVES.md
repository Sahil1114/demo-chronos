# Chronos — Detailed HLD (Deep Dives)

This document covers the detailed design of the four most architecturally significant subsystems. This is the bridge between System Design and Low-Level Design (LLD).

---

## 1. iOS Foreground Runner — Coroutine Structure & DB Watch

### The Core Problem
`BGTaskScheduler` is OS-controlled. Between the time a user schedules a task and the time iOS decides to wake the app in the background, hours can pass. The foreground runner solves this by executing tasks immediately whenever the app is open — making the "app is open" window the primary reliability mechanism on iOS.

### The State Machine
The runner is always in one of three states:

```
                    App launches / comes to foreground
                              │
                              ▼
                    ┌─────────────────┐
               ┌───►   INITIALIZING  │
               │    └────────────────┘
               │           │
               │    1. Orphan Sweep (RUNNING → ENQUEUED)
               │    2. APPEND Chain Recovery Sweep
               │    3. Resume DB observation
               │           │
               │           ▼
               │    ┌─────────────────┐
               │    │    WATCHING     │◄─────────────────────┐
               │    └─────────────────┘                      │
               │           │                                 │
               │    Room DB emits new ENQUEUED task          │
               │           │                                 │
               │           ▼                                 │
               │    ┌─────────────────┐                      │
               │    │   EXECUTING     │                      │
               │    └─────────────────┘                      │
               │           │                                 │
               │    ┌──────┴──────────┐                      │
               │    │                 │                      │
               │  SUCCESS           RETRY/FAIL               │
               │    │                 │                      │
               │    ▼                 ▼                      │
               │  Update DB         Update DB ───────────────┘
               │                  (reschedule)
               │
               │    App goes to background
               └─── Cancel coroutine scope, submit BGTask
```

### Coroutine Structure

```
IosWorkManager
│
├── engineScope: CoroutineScope (SupervisorJob + Dispatchers.Default)
│    │
│    ├── Watcher Coroutine (launch — runs forever while app is open)
│    │    │
│    │    │   dao.observeEnqueuedTasks()   ← Room Flow, emits on every DB change
│    │    │        .collect { tasks ->
│    │    │            tasks.forEach { task ->
│    │    │                if (!activeJobs.contains(task.id)) {
│    │    │                    launchWorker(task)   ← launch a child coroutine
│    │    │                }
│    │    │            }
│    │    │        }
│    │
│    └── Worker Coroutines (one per running task)
│         │
│         ├── workerJob = launch {
│         │       dao.updateState(id, RUNNING)
│         │       try {
│         │           withTimeout(task.timeoutMs) {
│         │               val worker = registry.create(task.workerClassName, inputData)
│         │               val result = worker.doWork()
│         │               handleResult(result)
│         │           }
│         │       } catch (e: TimeoutCancellationException) {
│         │           dao.updateState(id, FAILED)
│         │       }
│         │   }
│         └── activeJobs[task.id] = workerJob
```

### Lifecycle Hooks (called from Swift AppDelegate)

```
Application Events              IosWorkManager Response
─────────────────────────────────────────────────────
applicationDidBecomeActive  ──► engineScope.start()
                                initializationSweep()
                                startWatcherCoroutine()

applicationDidEnterBackground──► engineScope.cancel()
                                 submitBGTaskRequests()

applicationWillTerminate    ──► engineScope.cancel()
                                (Room DB preserves state)
```

### Concurrency Control
- `activeJobs: ConcurrentHashMap<String, Job>` prevents the same task from being launched twice if the DB emits multiple times.
- Each worker coroutine uses `SupervisorJob` so one failing worker does not cancel other running workers.
- The watcher coroutine itself is the only "infinite" coroutine. All worker coroutines are finite and remove themselves from `activeJobs` on completion.

---

## 2. WorkChain Data Model — Dependencies & Evaluation

### The Core Problem
A chain `A → B → C` means:
- B must not start until A completes.
- C must not start until B completes.
- A's output `WorkData` must be passed as B's input.
- If A fails, we must evaluate `ChainFailurePolicy` before deciding what to do with B and C.
- This chain state must survive app kills — a process restart between A completing and B starting must not lose B.

### How Chains are Stored

```
WorkRequestEntity table:
┌────────────┬─────────────┬──────────┬─────────┬────────────┐
│ id         │ workerClass │ chainId  │ chain   │ state      │
│            │             │          │ Order   │            │
├────────────┼─────────────┼──────────┼─────────┼────────────┤
│ "task-A"   │ FetchWorker │ "chain1" │ 0       │ SUCCEEDED  │
│ "task-B"   │ ProcessWkr  │ "chain1" │ 1       │ ENQUEUED   │
│ "task-C"   │ UploadWkr   │ "chain1" │ 2       │ ENQUEUED   │
└────────────┴─────────────┴──────────┴─────────┴────────────┘

WorkDependencyEntity table:
┌──────────────┬─────────────┬──────────────────────┐
│ parentWorkId │ childWorkId │ chainFailurePolicy    │
├──────────────┼─────────────┼──────────────────────┤
│ "task-A"     │ "task-B"    │ STOP_ON_FAILURE       │
│ "task-B"     │ "task-C"    │ STOP_ON_FAILURE       │
└──────────────┴─────────────┴──────────────────────┘
```

### Chain Evaluation Logic (executed by the engine before launching a task)

```
isTaskReadyToRun(taskId):
    │
    ├── Query WorkDependencyEntity for all rows where childWorkId = taskId
    │
    ├── No parents? ──► Task is independent, run immediately
    │
    └── Has parents?
         │
         ├── All parents SUCCEEDED?
         │    └──► Merge parent outputData → this task's inputData → run
         │
         ├── Any parent FAILED?
         │    └── Check chainFailurePolicy:
         │         ├── STOP_ON_FAILURE  → mark this task CANCELLED, propagate to children
         │         └── CONTINUE_ON_FAILURE → run with empty inputData
         │
         └── Any parent still RUNNING / ENQUEUED?
              └──► Wait. Room Flow will re-evaluate when parent state changes.
```

### Output Data Merging (multiple parents → one child)

When a task has multiple parents (e.g. `[A, B] → C`), each parent produces its own `outputData`. The merge strategy is:

```
parentOutputMergeStrategy options:
├── MERGE_KEYS   → combine all parent output maps, last-write-wins on key collision
├── FIRST_WINS   → use only the first completed parent's output
└── LATEST_WINS  → use only the most recently completed parent's output
```

### APPEND Chain Crash Recovery Sweep

This sweep runs at app startup, before `runPendingTasksNow()`:

```
SELECT d.childWorkId
FROM WorkDependencyEntity d
JOIN WorkRequestEntity parent ON d.parentWorkId = parent.id
JOIN WorkRequestEntity child  ON d.childWorkId  = child.id
WHERE parent.state = 'SUCCEEDED'
  AND child.state  = 'ENQUEUED'
```

Any tasks found in this query are immediately promoted to ready-to-run. Without this sweep, a chain where A completed but the app was killed before B was promoted will be permanently stuck.

### Chain State Flow Diagram

```
 enqueue() called with chain [A → B → C]
          │
          ▼
 All tasks inserted to DB:  A=ENQUEUED, B=ENQUEUED, C=ENQUEUED
 Dependencies inserted:     A→B, B→C
          │
          ▼
 Engine evaluates A:
   No parents → A is ready → A starts
   A state = RUNNING
          │
      ┌───┴────────────────────┐
   A SUCCESS                A FAILURE
      │                        │
      ▼                        ▼
  A state = SUCCEEDED      A state = FAILED
  Store A.outputData        Check B's chainFailurePolicy
          │                   │
          ▼                   ├── STOP_ON_FAILURE
  Engine evaluates B:         │   B=CANCELLED, C=CANCELLED
  Parent A = SUCCEEDED        │
  Merge A.outputData          └── CONTINUE_ON_FAILURE
  into B.inputData                B runs with empty inputData
  B state = RUNNING
          │
      B SUCCESS
          │
          ▼
  Engine evaluates C...
```

---

## 3. KSP Processor Internals — Code Generation

### The Core Problem
Kotlin/Native (iOS) has no `Class.forName()` or reflection. When a task is read from the Room DB, all we have is a `String` like `"com.example.SyncWorker"`. We need to instantiate the class from that string. Our KSP processor generates a compile-time lookup map so no runtime reflection is needed.

### Module Structure

```
chronos-processor/               ← Pure JVM Kotlin module (not KMP)
├── src/main/kotlin/
│   ├── RegisterWorkerProcessor.kt   ← Main SymbolProcessor logic
│   ├── RegisterWorkerProvider.kt    ← SymbolProcessorProvider (entry point)
│   └── WorkerCodeGenerator.kt       ← Writes the generated .kt file
└── src/main/resources/
    └── META-INF/services/
        └── com.google.devtools.ksp.processing.SymbolProcessorProvider
            ← Contains: "io.chronos.processor.RegisterWorkerProvider"
```

### Processing Pipeline

```
KSP invokes RegisterWorkerProvider.create()
         │
         ▼
RegisterWorkerProcessor.process(resolver)
         │
         ▼
Step 1: Find all annotated classes
         │
         │  resolver.getSymbolsWithAnnotation(
         │      "io.chronos.annotation.RegisterWorker"
         │  )
         │  .filterIsInstance<KSClassDeclaration>()
         │
         ▼
Step 2: Validate each class
         │
         ├── Must extend KmpWorker
         ├── Must not be abstract
         ├── Must have a (WorkData) constructor
         │   OR have a factory = SomeFactory::class specified
         └── Must be in a source set visible to the current target
         │
         ▼
Step 3: Collect worker descriptors
         │
         │  data class WorkerDescriptor(
         │      val qualifiedName: String,     // "com.example.SyncWorker"
         │      val simpleName: String,        // "SyncWorker"
         │      val hasCustomFactory: Boolean,
         │      val factoryQualifiedName: String?
         │  )
         │
         ▼
Step 4: Generate the registry file
         │
         │  codeGenerator.createNewFile(
         │      dependencies = Dependencies(aggregating = true, *sources),
         │      packageName = "io.chronos.generated",
         │      fileName = "GeneratedWorkerRegistry"
         │  )
         │
         ▼
Step 5: Write generated Kotlin source
```

### Generated File Structure

```kotlin
// FILE: io/chronos/generated/GeneratedWorkerRegistry.kt
// AUTO-GENERATED BY chronos-processor — DO NOT EDIT

package io.chronos.generated

import io.chronos.core.KmpWorkerRegistry
import io.chronos.core.WorkData
import com.example.SyncWorker
import com.example.upload.UploadWorker
import com.example.upload.UploadWorkerFactory

/**
 * Registers all workers annotated with @RegisterWorker.
 * Call this once during Chronos initialization.
 */
fun KmpWorkerRegistry.registerGeneratedWorkers() {
    // Auto-generated constructor factory
    register(
        className = "com.example.SyncWorker",
        factory = { data: WorkData -> SyncWorker(data) }
    )

    // Custom DI factory (developer-provided)
    register(
        className = "com.example.upload.UploadWorker",
        factory = UploadWorkerFactory()
    )
}
```

### How KSP handles multiplatform source sets

```
KSP runs once per compilation target:

  kspAndroid          → generates for Android
  kspIosArm64         → generates for iOS (arm64)
  kspIosSimulatorArm64→ generates for iOS Simulator
  kspJvm              → generates for JVM Desktop

Each compilation sees only the workers declared in its own
source set hierarchy (commonMain + platformMain).

A worker declared in androidMain only will NOT appear
in the iOS-generated registry — which is correct behavior.
```

### Incremental Processing

KSP supports incremental processing — if `SyncWorker.kt` hasn't changed, KSP does not re-process it. We mark our processor as `aggregating = true` (because the generated file aggregates all workers into one), which means:
- Any change to **any** annotated worker file triggers regeneration of the entire registry file.
- This is safe and correct. The registry is one file that maps everything.

---

## 4. Constraint Checker Pipeline — Multi-Constraint Evaluation

### The Core Problem
Before any worker runs, the engine must verify that **all** declared constraints are satisfied simultaneously. If any constraint fails, the task must not run. The pipeline must:
1. Be extensible (add new constraints without changing the engine).
2. Be testable (each checker is independently unit-testable).
3. Be composable (AND logic across all constraints).
4. Provide a reason for failure (for debug logging).

### The Pipeline Architecture

```
WorkRequest arrives at execution engine
         │
         ▼
┌────────────────────────────────────────┐
│         ConstraintCheckerPipeline      │
│                                        │
│  checkers: List<ConstraintChecker>     │
│  ├── NetworkConstraintChecker          │
│  ├── BatteryConstraintChecker          │
│  ├── ChargingConstraintChecker         │
│  ├── StorageConstraintChecker          │
│  ├── DeviceIdleConstraintChecker       │
│  └── TimeWindowConstraintChecker       │
│                                        │
│  fun check(request): ConstraintResult  │
└────────────────────────────────────────┘
         │
         ├── ALL pass → ConstraintResult.Satisfied → run worker
         └── ANY fail → ConstraintResult.Unsatisfied(reason) → reschedule
```

### The Checker Interface

```kotlin
// commonMain
interface ConstraintChecker {
    // Returns null if constraint passes, or a reason string if it fails
    fun check(constraints: WorkConstraints): String?
}

sealed class ConstraintResult {
    object Satisfied : ConstraintResult()
    data class Unsatisfied(val failedConstraints: List<String>) : ConstraintResult()
}

class ConstraintCheckerPipeline(
    private val checkers: List<ConstraintChecker>
) {
    fun evaluate(constraints: WorkConstraints): ConstraintResult {
        val failures = checkers
            .mapNotNull { checker -> checker.check(constraints) }
        
        return if (failures.isEmpty()) ConstraintResult.Satisfied
               else ConstraintResult.Unsatisfied(failures)
    }
}
```

### Individual Checker Logic Per Platform

#### NetworkConstraintChecker
```
constraints.requiredNetworkType:

NOT_REQUIRED  → Always return null (pass)
CONNECTED     → Check if any network interface is up
               Android: ConnectivityManager (native)
               iOS:     NWPathMonitor (native)
               JVM:     NetworkInterface.getNetworkInterfaces()
                        .asSequence().any { it.isUp && !it.isLoopback }
UNMETERED     → Check if current network is Wi-Fi / unmetered
               Android: Native via ConnectivityManager capabilities
               iOS:     NWPath.isExpensive (false = unmetered)
               JVM:     No-op → always passes (documented behavior)
```

#### BatteryConstraintChecker
```
constraints.requiresBatteryNotLow:

false         → Always return null (pass)
true          → Check battery level
               Android: BatteryManager (native)
               iOS:     UIDevice.current.batteryLevel
                        (enable monitoring: UIDevice.current.isBatteryMonitoringEnabled = true)
                        Pass if batteryLevel > 0.20 (20%)
               JVM:     If plugged in → pass
                        If on battery → use SystemInfo if available
                        If battery state unknown → pass (desktop assumption)
```

#### ChargingConstraintChecker
```
constraints.requiresCharging:

false         → Always return null (pass)
true          → Check if plugged in
               Android: BatteryManager.isCharging (native)
               iOS:     UIDevice.current.batteryState == .charging || .full
               JVM:     SystemInfo / OperatingSystemMXBean
                        If state unknown → pass (desktop assumption)
```

#### StorageConstraintChecker
```
constraints.requiresStorageNotLow:

false         → Always return null (pass)
true          → Check available storage
               Android: Native WorkManager handles this
               iOS:     URL.resourceValues(forKeys: [.volumeAvailableCapacityKey])
                        Pass if available > 50MB (configurable threshold)
               JVM:     File(dbPath).usableSpace > threshold
```

#### DeviceIdleConstraintChecker
```
constraints.requiresDeviceIdle:

false         → Always return null (pass)
true          → Android: WorkManager handles Doze mode natively
               iOS:     Always pass (no-op, documented)
               JVM:     Always pass (no-op, documented)
```

#### TimeWindowConstraintChecker
```
constraints.allowedTimeWindow:

null          → Always return null (pass)
TimeWindow    → Check current time against window
               ALL PLATFORMS (pure Kotlin logic):

               val now = Clock.System.now()
                   .toLocalDateTime(timeWindow.timeZone)
               val currentHour = now.hour

               if startHour <= endHour:
                   pass if currentHour in startHour..endHour
               else (overnight window e.g. 22:00-06:00):
                   pass if currentHour >= startHour
                        || currentHour <= endHour

               This is 100% common code — no platform actuals needed.
               Uses kotlinx-datetime for correct timezone handling.
```

### How the Pipeline is Built Per Platform

```kotlin
// androidMain
// Android delegates most constraints to native WorkManager.
// The pipeline is minimal — only TimeWindow needs checking
// since WorkManager handles the rest natively.
actual fun buildConstraintPipeline(clock: KmpClock): ConstraintCheckerPipeline =
    ConstraintCheckerPipeline(listOf(
        TimeWindowConstraintChecker(clock)
    ))

// iosMain
// Full pipeline — all constraints checked in Kotlin
actual fun buildConstraintPipeline(clock: KmpClock): ConstraintCheckerPipeline =
    ConstraintCheckerPipeline(listOf(
        IosNetworkConstraintChecker(),
        IosBatteryConstraintChecker(),
        IosChargingConstraintChecker(),
        IosStorageConstraintChecker(),
        TimeWindowConstraintChecker(clock)
        // DeviceIdleConstraintChecker omitted — no-op on iOS
    ))

// jvmMain
actual fun buildConstraintPipeline(clock: KmpClock): ConstraintCheckerPipeline =
    ConstraintCheckerPipeline(listOf(
        JvmNetworkConstraintChecker(),
        JvmStorageConstraintChecker(),
        TimeWindowConstraintChecker(clock)
        // Battery/Charging → desktop assumptions apply
        // DeviceIdle → no-op
    ))
```

### Constraint Failure Handling

When `ConstraintResult.Unsatisfied` is returned:

```
iOS/JVM Engine:
├── Log failed constraints (if debug logging enabled)
│   "[Chronos] BLOCKED | id=abc | reasons=[Network not connected, Battery low]"
│
├── Task state remains ENQUEUED (not changed to FAILED)
│
├── iOS: Re-check on next BGTask wake OR next app foreground event
└── JVM: Re-check on next daemon loop iteration
```

---

## Summary: How the Four Subsystems Connect

```
┌─────────────────────────────────────────────────────────────┐
│  Developer annotates workers with @RegisterWorker           │
│              │                                              │
│              ▼                                              │
│  KSP Processor (§3) generates registry at compile time      │
│              │                                              │
│              ▼                                              │
│  Developer calls enqueue(request) with WorkConstraints      │
│              │                                              │
│              ▼                                              │
│  iOS Foreground Runner (§1) wakes up via Room DB Flow       │
│              │                                              │
│              ▼                                              │
│  Constraint Pipeline (§4) evaluates all constraints         │
│              │                                              │
│    Satisfied │              │ Unsatisfied                   │
│              ▼              ▼                               │
│         Run worker       Reschedule,                        │
│              │           stay ENQUEUED                      │
│              ▼                                              │
│  WorkChain Model (§2) evaluates dependencies                │
│  Promotes child tasks when parent succeeds                  │
│  Applies ChainFailurePolicy when parent fails               │
└─────────────────────────────────────────────────────────────┘
```
