package io.github.sahilmakkar.chronos.roomDb

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor

@Database(
    entities = [WorkRequestEntity::class, WorkTagEntity::class, WorkDependencyEntity::class],
    version = 1
)
@ConstructedBy(WorkDatabaseConstructor::class)
abstract class WorkDatabase : RoomDatabase() {

    abstract fun workDao(): WorkDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object WorkDatabaseConstructor : RoomDatabaseConstructor<WorkDatabase> {
    override fun initialize(): WorkDatabase
}
