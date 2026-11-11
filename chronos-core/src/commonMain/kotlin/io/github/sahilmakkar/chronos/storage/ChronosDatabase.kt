package io.github.sahilmakkar.chronos.storage

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor

/**
 * Room database definition for Chronos persistent task records.
 */
@Database(
    entities = [TaskEntity::class, TaskTagEntity::class, TaskDependencyEntity::class],
    version = 1
)
@ConstructedBy(ChronosDatabaseConstructor::class)
internal abstract class ChronosDatabase : RoomDatabase() {

    /**
     * Returns the [TaskDao] for performing persistent queries and mutations.
     */
    abstract fun taskDao(): TaskDao
}

/**
 * Platform constructor expect object required by Room KMP for database instantiation.
 */
@Suppress("NO_ACTUAL_FOR_EXPECT")
internal expect object ChronosDatabaseConstructor : RoomDatabaseConstructor<ChronosDatabase> {
    override fun initialize(): ChronosDatabase
}
