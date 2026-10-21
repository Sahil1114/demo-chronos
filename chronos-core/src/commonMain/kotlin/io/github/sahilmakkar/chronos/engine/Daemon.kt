package io.github.sahilmakkar.chronos.engine

import kotlinx.coroutines.CoroutineScope

internal expect class Daemon(
    coroutineScope: CoroutineScope,
    coordinator: TaskCoordinator
) {
    fun start()
    fun stop()
}
