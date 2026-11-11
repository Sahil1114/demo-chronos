package io.github.sahilmakkar.chronos.storage

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.sahilmakkar.chronos.PlatformSupport
import kotlinx.coroutines.Dispatchers

/**
 * Constructs an Android Room database instance backed by SQLite.
 *
 * Configures the database file inside the application's internal database directory (`chronos_work.db`),
 * attaches [BundledSQLiteDriver] for native SQLite operations, and dispatches queries on [Dispatchers.IO].
 *
 * @param support Platform support provider containing the Android [android.content.Context].
 * @return An initialized [ChronosDatabase] ready for concurrent task access.
 */
internal actual fun createRoomDatabase(support: PlatformSupport): ChronosDatabase {
    val context = support.context
    val dbFile = context.getDatabasePath("chronos_work.db")
    return Room.databaseBuilder<ChronosDatabase>(
        context = context,
        name = dbFile.absolutePath
    ).setDriver(driver = BundledSQLiteDriver())
        .setQueryCoroutineContext(context = Dispatchers.IO)
        .build()
}
