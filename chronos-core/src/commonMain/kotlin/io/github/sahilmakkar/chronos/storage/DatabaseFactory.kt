package io.github.sahilmakkar.chronos.storage

import io.github.sahilmakkar.chronos.PlatformSupport

/**
 * Expect declaration for building platform-specific Room [ChronosDatabase] instances.
 */
internal expect fun createRoomDatabase(support: PlatformSupport): ChronosDatabase
