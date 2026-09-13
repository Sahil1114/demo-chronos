package io.github.sahilmakkar.chronos.roomDb

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.sahilmakkar.chronos.PlatformSupport
import kotlinx.coroutines.Dispatchers

actual fun createRoomDatabase(support: PlatformSupport): WorkDatabase {
    val context = support.context
    val dbFile = context.getDatabasePath("chronos_work.db")
    return Room.databaseBuilder<WorkDatabase>(
        context = context,
        name = dbFile.absolutePath
    ).setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}