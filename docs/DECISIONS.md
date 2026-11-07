# Architectural and Design Decisions

## 1. Pre-release Schema Migration for UUID Format (Item 0)

### Context
Earlier local pre-release runs of Chronos generated task UUIDs using `Uuid.random().toHexString()` (32-character hex without dashes), which caused runtime failures on Android when parsed with `java.util.UUID.fromString`. The default ID generator has been updated to use `Uuid.random().toString()` (standard 36-character canonical dashed UUID).

### Decision
Pre-release Room schemas and local databases are not migrated for dashless UUIDs. Since this only affects local developer devices prior to the `0.1.0-alpha` release, developers should clear application data or uninstall previous builds.

## 2. Android Process Death and Background Execution Architecture (Item 3)

### Context
On Android, if the operating system terminates the application process, in-process loops (such as the Daemon) cease execution. Background tasks must survive process death and run when constraints and delays are satisfied.
Previously, `AndroidTaskExecutor` delegated individual tasks to WorkManager with JSON payload mapping inside `androidx.work.Data`, introducing:
1. A 10 KB Data payload limit.
2. Unbounded waits on `WorkInfo` flows which blocked coordinator passes when WorkManager constraints were unsatisfied.
3. Divergent execution models between Android and JVM/iOS.
4. Redundant execution risks when `sweepOrphans` marked tasks as ENQUEUED while WorkManager still had them in-flight.

### Decision
We adopt **Option B**: WorkManager is used exclusively as an OS-level wake-up trigger (`ChronosWakeWorker`).
- Tasks are executed uniformly across all platforms using `InProcessTaskExecutor`.
- `ChronosWakeScheduler` schedules a unique WorkManager task (`"chronos_wake"`, `ExistingWorkPolicy.REPLACE`) with initial delay matching the next due task.
- If the next due task specifies network or battery constraints, those constraints are mirrored in the WorkManager wake request.
- `ChronosWakeWorker` wakes up the process and invokes `Chronos.instance.runPendingTasksNow()`, then reschedules the next wake-up.
- `AndroidTaskExecutor`, `AndroidDelegatingWorker`, and `WorkManagerRequestMapper` are removed.

## 3. Retry Semantics Definition for maxRetries (Item 7)

### Context
Previously, `TaskCoordinator.handleResult` evaluated retry exhaustion with `attempt >= entity.maxRetries`. Since `runAttemptCount` is incremented on every execution (starting at 1 for the initial attempt), a task with `maxRetries = 3` was terminated after only 3 total runs (1 initial execution + 2 retries), causing off-by-one behavior contrary to user expectations.

### Decision
`maxRetries` strictly specifies the number of retry attempts permitted following the initial run. A task with `maxRetries = N` is permitted up to `N + 1` total executions (1 initial run + N retries).
In `TaskCoordinator.handleResult`, the check is updated to:
`attempt > entity.maxRetries` (retries are exhausted only when attempts exceed `maxRetries`).

## 4. Omission of iosX64 Target (Item 8)

### Context
We evaluated adding `iosX64` (Intel Mac simulator) support to `chronos-core` alongside `iosArm64` and `iosSimulatorArm64` (Apple Silicon simulators).
However, `androidx.room3:room3-runtime:3.0.3` does not publish pre-compiled artifacts or metadata for the `ios_x64` Kotlin/Native target.

### Decision
`chronos-core` and `chronos-testing` target `iosArm64` (real iOS devices) and `iosSimulatorArm64` (modern Apple Silicon iOS simulators). `iosX64` is excluded until upstream `androidx.room3` adds support for Intel simulator targets.

## 5. Retention of com.android.library Plugin for Room 3 / KMP Compatibility (Item 9)

### Context
AGP 9.0 introduces `com.android.kotlin.multiplatform.library` as a new mechanism for multiplatform Android libraries. However, `androidx.room3:room3-compiler` and the Room Gradle plugin expect the traditional Android components extension provided by `com.android.library`. Migrating immediately causes unresolved dependencies on Room processor and schema export tasks.

### Decision
We retain `com.android.library` for `chronos-core` and `chronos-testing` while suppressing deprecation noise via `android.sync.suppressAgpWarnings=UNSUPPORTED_PROJECT_OPTION_USE,DEPRECATED_DSL`. Migration to `com.android.kotlin.multiplatform.library` will occur once AndroidX Room officially supports it.

## 6. Single-Module Structure for Sample Application (Item 9)

### Context
AGP 9.0 warns when `com.android.application` is applied in the same module as other multiplatform targets (Desktop and iOS).

### Decision
We preserve the unified `:sample` multiplatform structure for clarity and developer ergonomics in this demo repository, rather than fragmenting the sample into separate `:sample-core`, `:sample-android`, `:sample-desktop`, and `:sample-ios` projects.

## 7. Process-Wide Singleton Retention (`Chronos.instance`) (Item 10)

### Context
An earlier plan considered moving the singleton out of `chronos-core` and requiring dependency injection or manual instance passing.
However, Android background execution requires a process-wide instance because `ChronosWakeWorker` is instantiated reflectively by Android's `WorkManager` when the OS wakes the application process. At that point, the worker needs access to `Chronos.instance` to invoke `runPendingTasksNow()`.

### Decision
We retain `Chronos.instance`, `Chronos.initialize(support, registry)`, `Chronos.shutdown()`, and `Chronos.setForTesting(instance)` in `chronos-core`. This matches WorkManager's own singleton architecture and ensures headless background worker triggers function reliably without requiring third-party DI frameworks.
