package io.github.sahilmakkar.chronos.storage

import io.github.sahilmakkar.chronos.PlatformSupport

expect fun createRoomDatabase(support: PlatformSupport): ChronosDatabase
