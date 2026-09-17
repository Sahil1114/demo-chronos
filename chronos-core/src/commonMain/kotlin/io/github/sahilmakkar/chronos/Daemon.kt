package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.roomDb.executor.WorkChainCoordinator
import kotlinx.coroutines.CoroutineScope

expect class Daemon(
    coroutineScope: CoroutineScope,
    coordinator: WorkChainCoordinator
) {
    fun start()
    fun stop()
}
