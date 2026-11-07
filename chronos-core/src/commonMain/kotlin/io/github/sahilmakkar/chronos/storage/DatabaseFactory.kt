package io.github.sahilmakkar.chronos.storage

import io.github.sahilmakkar.chronos.PlatformSupport

internal expect fun createRoomDatabase(support: PlatformSupport): ChronosDatabase
