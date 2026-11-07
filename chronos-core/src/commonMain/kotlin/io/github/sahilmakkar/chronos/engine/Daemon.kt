package io.github.sahilmakkar.chronos.engine

import io.github.sahilmakkar.chronos.storage.ChronosDatabase
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Clock

/**
 * Platform background daemon managing the active task dispatch loop.
 */
internal expect class Daemon(
    coroutineScope: CoroutineScope,
    coordinator: TaskCoordinator,
    db: ChronosDatabase,
    clock: Clock = Clock.System
) {
    fun start()
    fun stop()
}
