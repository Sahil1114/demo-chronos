@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package io.github.sahilmakkar.chronos.db

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * Apple iOS/macOS actual implementation of [io.github.sahilmakkar.chronos.db.createRoomDatabase] that instantiates the Room database
 * under the standard iOS/macOS Documents directory.
 */
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
