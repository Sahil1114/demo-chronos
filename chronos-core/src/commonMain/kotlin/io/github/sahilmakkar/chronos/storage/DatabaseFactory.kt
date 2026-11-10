package io.github.sahilmakkar.chronos.storage

import io.github.sahilmakkar.chronos.PlatformSupport

/**
 * Platform-agnostic factory function for building and initializing the Room [ChronosDatabase].
 *
 * Each target platform implements this factory to configure the database file location and SQLite driver:
 * - **Android**: Uses the application's private database directory (`/data/data/<pkg>/databases/chronos_work.db`).
 * - **iOS/Apple**: Uses the application sandbox documents directory (`NSDocumentDirectory`).
 * - **JVM**: Uses the current user's home directory (`~/.chronos/chronos_work.db`).
 *
 * All platforms utilize [androidx.sqlite.driver.bundled.BundledSQLiteDriver] for consistent, high-performance
 * SQLite operations with WAL (Write-Ahead Logging) enabled.
 *
 * @param support Platform-specific initialization parameters (e.g. Android [android.content.Context]).
 * @return A fully initialized, thread-safe [ChronosDatabase] instance.
 */
internal expect fun createRoomDatabase(support: PlatformSupport): ChronosDatabase
