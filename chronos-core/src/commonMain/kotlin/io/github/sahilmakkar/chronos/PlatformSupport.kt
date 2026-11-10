package io.github.sahilmakkar.chronos

/**
 * Platform-specific environment bindings required to construct and initialize Chronos.
 *
 * Encapsulates target-specific context and system services:
 * - **Android:** Wraps an Android [android.content.Context] (e.g. `ApplicationContext`), used for SQLite database
 *   creation, WorkManager scheduling, and system service monitoring.
 * - **iOS/Apple:** Parameterless constructor initializing Apple Foundation and `BGTaskScheduler` infrastructure.
 * - **JVM:** Parameterless constructor configuring bundled SQLite and native threads.
 *
 * @see Chronos.Companion.initialize
 */
public expect class PlatformSupport
