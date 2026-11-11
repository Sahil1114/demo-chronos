package io.github.sahilmakkar.chronos.storage

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.sahilmakkar.chronos.PlatformSupport
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * Constructs an Apple/iOS Room database instance backed by SQLite.
 *
 * Locates the application sandbox `NSDocumentDirectory` via [NSFileManager], constructs the database
 * path at `<Documents>/chronos_work.db`, configures [BundledSQLiteDriver], and binds queries to [Dispatchers.IO].
 *
 * @param support Platform support provider for Apple Darwin environments.
 * @return An initialized [ChronosDatabase] ready for concurrent task access.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun createRoomDatabase(support: PlatformSupport): ChronosDatabase {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null
    )
    val path = documentDirectory?.path ?: ""
    val dbFile = "$path/chronos_work.db"
    return Room.databaseBuilder<ChronosDatabase>(
        name = dbFile
    ).setDriver(driver = BundledSQLiteDriver())
        .setQueryCoroutineContext(context = Dispatchers.IO)
        .build()
}
