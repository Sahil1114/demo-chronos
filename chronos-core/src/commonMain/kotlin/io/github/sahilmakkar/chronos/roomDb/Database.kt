package io.github.sahilmakkar.chronos.roomDb

import io.github.sahilmakkar.chronos.PlatformSupport

expect fun createRoomDatabase(support: PlatformSupport): WorkDatabase
