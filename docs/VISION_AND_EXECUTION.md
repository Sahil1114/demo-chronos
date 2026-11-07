# Chronos — Vision & Execution

> "Build the library you wish existed."

This document captures **why** we are building Chronos, **what we believe** about how it should be built, and **how we will execute** each phase without cutting corners. For the technical blueprint (phases, schema, APIs), see `project_plan.md`.

---

## 1. The Vision

### Why does this library exist?

Android's `WorkManager` is one of the most thoughtfully engineered APIs in the entire Android ecosystem — persistent, constraint-aware, battery-efficient, observable. iOS developers have `BGTaskScheduler`, which is the opposite: opaque, OS-controlled, underpowered, and poorly documented in a cross-platform context.

In the Kotlin Multiplatform ecosystem today, there is **no library that bridges these two worlds credibly**. Existing attempts either:
- Treat iOS as an afterthought (thin wrappers with no persistence)
- Overclaim reliability they cannot provide
- Miss critical developer experience gaps (no test doubles, no DI support, no constraint matrix)

**Chronos exists to be the first library that takes both platforms seriously.**

It will:
1. **Delegate to the best native implementation where one exists** (Android WorkManager).
2. **Build a proper, Room-backed scheduling engine where one doesn't** (iOS, JVM Desktop).
3. **Be honest about platform limitations** — document them at the API level, not just in a README footnote.
4. **Be built by someone who actually deeply understands each platform** — which is why we learn before we build.

---

## 2. Guiding Principles

These are the non-negotiable values that guide every technical decision in this project. When in doubt, refer back to these.

### 2.1 Honesty over Convenience
If iOS cannot guarantee a periodic task interval, we say so — loudly, at the API level (`@BestEffortOnIOS`), in the README, and in the capability matrix. We do not paper over platform limitations with optimistic language. Users who understand the constraints will build better apps.

### 2.2 Learn Before You Build
We do not write a single line of `chronos-core` code until we have built isolated sandbox experiments for:
- **AndroidX WorkManager** — every constraint, every policy, every edge case
- **iOS BGTaskScheduler** — real device, real OS throttling, real disappointment
- **Room KMP** — real iOS device, stress tested, not simulator
- **KSP Processor** — generates code on both Android and iOS targets

This is not optional. Skipping this leads to APIs designed around wrong assumptions that are painful to fix after the fact.

### 2.3 Test Seams First
`FakeClock` and `TestChronos` stubs are created in Phase 3b — *before* the platform implementations. Any scheduling logic written without a controllable clock is logic that cannot be unit tested without real time delays. This is unacceptable for a scheduling library.

### 2.4 Dependency Minimalism
The core library (`chronos-core`) will have as few transitive dependencies as possible:
- **Room KMP + KSP** — required, no alternative
- **Kotlin Coroutines** — required, already in any KMP project
- **No Koin, no Ktor, no kotlinx.serialization** — the library is DI-agnostic and self-contained

We write our own lightweight `WorkData` serializer for primitives rather than pulling in a JSON library. This keeps transitive dependency conflicts near zero for library consumers.

### 2.5 Ship Platforms Correctly, Not Quickly
It is better to ship Android + iOS with correct behavior than to rush all three platforms and have silent failures on JVM Desktop. Each platform gets its own implementation phase, its own test coverage, and its own documented capability tier.

---

## 3. Learning Prerequisites (Mandatory Before Phase 1)

### Step 1 — AndroidX WorkManager Deep Dive
Build a standalone Android project that exercises every edge case of the official WorkManager API.

**Must verify:**
- [ ] `OneTimeWorkRequest` and `PeriodicWorkRequest` lifecycle
- [ ] All constraint types (network, charging, battery, storage, device idle)
- [ ] `beginWith().then()` chaining and `WorkContinuation`
- [ ] All three `ExistingWorkPolicy` values (`KEEP`, `REPLACE`, `APPEND`)
- [ ] `Flow<WorkInfo>` observation in a ViewModel
- [ ] Retry + exponential backoff behavior
- [ ] What happens on: app kill, device reboot, crash mid-task
- [ ] `setProgress()` and observing intermediate progress

**Why:** You cannot design a clean abstraction over something you don't understand deeply.

---

### Step 2 — iOS BGTaskScheduler Real-Device Spike
Build a standalone iOS app (Swift, plain Xcode project) that exercises BGTaskScheduler end-to-end on a **real device**.

**Must verify:**
- [ ] Register a `BGProcessingTask` identifier in `Info.plist`
- [ ] Submit a `BGProcessingTaskRequest` from Swift
- [ ] Handle the task callback and call `setTaskCompleted(success:)`
- [ ] Test background launch using Xcode simulator debugger command:
      ```
      e -l objc -- (void)[[BGTaskScheduler sharedScheduler]
          _simulateLaunchForIdentifier:@"com.example.sync"]
      ```
- [ ] Observe what "best-effort" actually means — how long does iOS defer tasks?
- [ ] Understand that the OS decides *when* your task runs, not you
- [ ] Measure real battery/network constraint enforcement timing

**Why:** iOS background execution is the highest-risk part of this project. Discovering its constraints here prevents weeks of rework in Phase 6.

---

### Step 3 — Room KMP Stress Test on Real iOS Device
Build a small, standalone KMP project (not Chronos) that exercises Room KMP on iOS.

**Must verify:**
- [ ] Entity, DAO, and Database setup with KSP code generation on iOS target
- [ ] `Flow`-based queries updating in real time
- [ ] WAL mode stability under concurrent reads/writes
- [ ] Thread safety during simulated app suspension/resume cycles
- [ ] Data integrity after force-quit mid-write
- [ ] Schema export and basic migration

**Exit criteria:** If WAL mode causes data loss or write failures on 2+ real device models, we switch to SQLDelight before committing to Room KMP in Phase 4.

---

### Step 4 — Custom KSP Processor Prototype
Build a minimal KSP processor that generates Kotlin source code from an annotation.

**Must verify:**
- [ ] `SymbolProcessorProvider`, `SymbolProcessor`, and `CodeGenerator` setup
- [ ] Find classes annotated with `@MyAnnotation` and generate a simple `.kt` file
- [ ] Verify generated output appears in the correct source set for both Android and iOS targets
- [ ] Understand how KSP handles multiplatform source sets

**Why:** Our KSP processor is a core differentiator. It must work on iOS (Kotlin/Native) and Android before we build the full `chronos-processor` module.

---

## 4. Strategic Decisions (Already Locked)

These decisions have been discussed, debated, and finalized. They are not open for renegotiation without a strong technical reason.

| Decision | Choice | Rationale |
|---|---|---|
| Platform scope v1.0 | Android, iOS, JVM Desktop | macOS in backlog — similar to iOS but different API, adds surface area without proportional learning value |
| Android implementation | Delegate 100% to `androidx.work` | No reason to reimplement what Google does correctly |
| iOS/JVM persistence | Room KMP (pending Step 3 validation) | Modern, official Google library — if it fails validation, fallback is SQLDelight |
| DI strategy | DI-agnostic factory pattern, no bundled DI | Library consumers use Koin, Hilt, Dagger, or manual DI — we support all via `@RegisterWorker(factory=...)` |
| Worker registration | Custom KSP processor (`chronos-processor`) | Eliminates manual registration boilerplate, solves Kotlin/Native no-reflection constraint elegantly |
| WorkChain | Included in v1.0 | Core feature of WorkManager semantics, both `STOP_ON_FAILURE` and `CONTINUE_ON_FAILURE` policies |
| Alarm Manager / Background Downloader | Backlog | Out of v1.0 scope — increases library responsibility beyond scheduling |
| macOS | Backlog | `appleMain` source set structure is preserved so macOS can be added without restructuring |
| WASM/Web | Never | Browsers have no persistent background execution model |

---

## 5. What Success Looks Like

### For v1.0 (Library Launch)
- A developer can drop `chronos-core` into any KMP project, annotate their workers with `@RegisterWorker`, and schedule background tasks on Android, iOS, and JVM Desktop with a **single, unified API**.
- The library ships with a `chronos-testing` module providing `TestChronos` and `FakeClock` so teams can unit test their worker logic without a device.
- The README ships with the constraint support matrix, iOS degradation warnings, and a complete getting-started guide.
- All constraints (`network`, `charging`, `battery`, `storage`, `idle`, `timeWindow`) are documented with exactly what they do on each platform.

### For the Kotlin Multiplatform Ecosystem
- This becomes **the reference implementation** for how to build a KMP library that:
    - Wraps a native Android API correctly
    - Builds a custom persistence engine for iOS using Room KMP
    - Uses a custom KSP processor to solve the Kotlin/Native no-reflection problem
- Other KMP library authors can look at our KSP processor and Room KMP integration as a template.

---

## 6. Honest iOS Warnings (Non-Negotiable Documentation)

These must appear prominently in the README and in API KDoc. Not in a footnote. Not buried.

1. **BGTaskScheduler is OS-controlled.** Apple decides when background tasks run. Your requested schedule is a hint, not a guarantee. Tasks may be delayed by hours.
2. **Minimum 15-minute periodic interval** — and even this is optimistic. In practice, iOS may enforce far longer gaps based on app usage patterns, battery state, and thermal conditions.
3. **BGTaskScheduler requires `Info.plist` registration.** Every background task identifier must be declared at build time in the host app. This is a compile-time constraint, not runtime.
4. **The foreground queue executor is your primary iOS reliability mechanism.** `Chronos.instance.runPendingTasksNow()` called from `applicationDidBecomeActive` is more reliable for most use cases than background scheduling.
5. **Do not use local notifications as a background code execution mechanism.** Apple explicitly prohibits this in App Store Review Guidelines and will reject apps that do it.

---

## 7. Execution Rhythm

- **Discuss → Spike → Design → Build → Test → Document** — in that order, always.
- No phase starts without the previous phase's exit criteria being met.
- The BGTaskScheduler POC (Phase 2b) must complete before the common API is finalized (Phase 3).
- Room KMP iOS validation must pass before the database schema is locked (Phase 4).
- `FakeClock` must exist before any scheduling logic is written (Phase 3b, before Phase 4).
