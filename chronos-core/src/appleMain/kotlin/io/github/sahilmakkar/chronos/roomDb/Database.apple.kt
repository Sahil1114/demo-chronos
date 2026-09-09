package io.github.sahilmakkar.chronos.roomDb


import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * Apple iOS/macOS actual implementation of [io.github.sahilmakkar.chronos.roomDb.createRoomDatabase] that instantiates the Room database
 * under the standard iOS/macOS Documents directory.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun createRoomDatabase(): WorkDatabase {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null
    )
    val path = documentDirectory?.path ?: ""
    val dbFile = "$path/chronos_work.db"
    return Room.databaseBuilder<WorkDatabase>(
        name = dbFile
    ).setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
