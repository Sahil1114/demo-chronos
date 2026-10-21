package io.github.sahilmakkar.chronos.storage

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.sahilmakkar.chronos.PlatformSupport
import kotlinx.coroutines.Dispatchers

actual fun createRoomDatabase(support: PlatformSupport): ChronosDatabase {
    val context = support.context
    val dbFile = context.getDatabasePath("chronos_work.db")
    return Room.databaseBuilder<ChronosDatabase>(
        context = context,
        name = dbFile.absolutePath
    ).setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
