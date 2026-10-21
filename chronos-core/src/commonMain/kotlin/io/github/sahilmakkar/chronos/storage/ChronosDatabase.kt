package io.github.sahilmakkar.chronos.storage

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor

@Database(
    entities = [TaskEntity::class, TaskTagEntity::class, TaskDependencyEntity::class],
    version = 1
)
@ConstructedBy(ChronosDatabaseConstructor::class)
abstract class ChronosDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    @Deprecated("Use taskDao() instead", ReplaceWith("taskDao()"))
    fun workDao(): TaskDao = taskDao()
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object ChronosDatabaseConstructor : RoomDatabaseConstructor<ChronosDatabase> {
    override fun initialize(): ChronosDatabase
}

@Deprecated("Use ChronosDatabase instead", ReplaceWith("ChronosDatabase"))
typealias WorkDatabase = ChronosDatabase
