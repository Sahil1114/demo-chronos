package io.github.sahilmakkar.chronos.scheduler


/**
 * Platform-introspection contract. Not part of [WorkerScheduler] since
 * these are diagnostic/platform APIs, not scheduling concerns.
 *
 * - [platformCapabilities] — query what this platform supports
 * - [runPendingTasksNow] — only meaningful on iOS/JVM; Android no-ops it
 */
interface PlatformScheduler {
    suspend fun runPendingTasksNow()
}