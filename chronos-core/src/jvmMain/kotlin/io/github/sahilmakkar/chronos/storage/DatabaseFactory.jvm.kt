package io.github.sahilmakkar.chronos.storage

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.sahilmakkar.chronos.PlatformSupport
import kotlinx.coroutines.Dispatchers
import java.io.File

actual fun createRoomDatabase(support: PlatformSupport): ChronosDatabase {
    val homeDir = System.getProperty("user.home")
    val dbFile = File(homeDir, ".chronos/chronos_work.db")
    dbFile.parentFile.mkdirs()
    return Room.databaseBuilder<ChronosDatabase>(
        name = dbFile.absolutePath
    ).setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
