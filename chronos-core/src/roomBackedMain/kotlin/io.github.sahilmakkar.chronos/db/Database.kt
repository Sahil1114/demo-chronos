package io.github.sahilmakkar.chronos.db

/**
 * Creates and initializes the platform-specific Room [WorkDatabase] instance.
 */
expect fun createRoomDatabase(): WorkDatabase
//