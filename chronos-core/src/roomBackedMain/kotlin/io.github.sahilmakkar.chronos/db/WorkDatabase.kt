package io.github.sahilmakkar.chronos.db

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor

/**
 * The Room database that stores and manages enqueued, scheduled, and completed background tasks
 * along with their constraints, tags, and dependencies.
 */
@Database(
    entities = [WorkRequestEntity::class, WorkTagEntity::class, WorkDependencyEntity::class],
    version = 1
)
@ConstructedBy(WorkDatabaseConstructor::class)
abstract class WorkDatabase : RoomDatabase() {
    
    /**
     * Obtains the Data Access Object (DAO) for managing [WorkRequestEntity]s.
     */
    abstract fun workDao(): WorkDao
}

/**
 * Expected [RoomDatabaseConstructor] required by Room 3.0 KMP to compile and build
 * actual database implementation classes on platforms.
 */
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object WorkDatabaseConstructor : RoomDatabaseConstructor<WorkDatabase> {
    override fun initialize(): WorkDatabase
}
