package io.github.sahilmakkar.chronos.storage

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.sahilmakkar.chronos.PlatformSupport
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * Constructs a JVM Room database instance backed by bundled SQLite.
 *
 * Persists data to `~/.chronos/chronos_work.db` in the current user's home directory, ensuring
 * parent directories are created if absent, attaches [BundledSQLiteDriver], and assigns
 * query execution to [Dispatchers.IO].
 *
 * @param support Platform support provider for JVM environments.
 * @return An initialized [ChronosDatabase] ready for concurrent task access.
 */
internal actual fun createRoomDatabase(support: PlatformSupport): ChronosDatabase {
    val homeDir = System.getProperty("user.home")
    val dbFile = File(homeDir, ".chronos/chronos_work.db")
    dbFile.parentFile.mkdirs()
    return Room.databaseBuilder<ChronosDatabase>(
        name = dbFile.absolutePath
    ).setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
