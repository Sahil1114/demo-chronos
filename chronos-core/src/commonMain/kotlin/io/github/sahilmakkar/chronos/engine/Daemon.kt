package io.github.sahilmakkar.chronos.engine

import io.github.sahilmakkar.chronos.storage.ChronosDatabase
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Clock

/**
 * Platform background daemon managing the active task dispatch loop and scheduled execution timing.
 *
 * Implemented across Android, Apple, and JVM targets using target-optimized background scheduling.
 */
internal expect class Daemon(
    coroutineScope: CoroutineScope,
    coordinator: TaskCoordinator,
    db: ChronosDatabase,
    clock: Clock = Clock.System
) {
    /**
     * Starts the daemon event loop and registers background wake triggers.
     */
    fun start()

    /**
     * Stops the daemon event loop and unregisters any active background listeners.
     */
    fun stop()
}
