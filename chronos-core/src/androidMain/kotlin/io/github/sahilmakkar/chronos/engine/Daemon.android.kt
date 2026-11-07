package io.github.sahilmakkar.chronos.engine

import io.github.sahilmakkar.chronos.storage.ChronosDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Android background daemon orchestrating active task execution and periodic due-time loops.
 */
internal actual class Daemon actual constructor(
    private val coroutineScope: CoroutineScope,
    private val coordinator: TaskCoordinator,
    private val db: ChronosDatabase,
    private val clock: Clock
) {
    private val dueTimeLoop = DueTimeLoop(db, coordinator, clock)

    actual fun start() {
        coroutineScope.launch {
            coordinator.sweepOrphans()
            coordinator.sweepChainRecovery()
        }
        dueTimeLoop.start(coroutineScope)
    }

    actual fun stop() {
        dueTimeLoop.stop()
    }
}
