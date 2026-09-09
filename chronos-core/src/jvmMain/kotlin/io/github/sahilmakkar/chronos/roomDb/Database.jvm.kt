package io.github.sahilmakkar.chronos.roomDb

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.sahilmakkar.chronos.roomDb.WorkDatabase
import kotlinx.coroutines.Dispatchers
import java.io.File

actual fun createRoomDatabase(): WorkDatabase {
    val homeDir = System.getProperty("user.home")
    val dbFile = File(homeDir, ".chronos/chronos_work.db")
    dbFile.parentFile.mkdirs()
    return Room.databaseBuilder<WorkDatabase>(
        name = dbFile.absolutePath
    ).setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
