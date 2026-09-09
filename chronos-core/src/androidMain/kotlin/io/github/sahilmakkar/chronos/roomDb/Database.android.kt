package io.github.sahilmakkar.chronos.roomDb

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.sahilmakkar.chronos.ChronosContextHolder
import kotlinx.coroutines.Dispatchers

actual fun createRoomDatabase(): WorkDatabase {
    val context = ChronosContextHolder.appContext
    val dbFile = context.getDatabasePath("chronos_work.db")
    return Room.databaseBuilder<WorkDatabase>(
        context = context,
        name = dbFile.absolutePath
    ).setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}