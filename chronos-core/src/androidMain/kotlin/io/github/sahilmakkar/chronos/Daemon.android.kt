package io.github.sahilmakkar.chronos

import android.util.Log
import io.github.sahilmakkar.chronos.roomDb.executor.WorkChainCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val POLL_INTERVAL_MS = 1000L

actual class Daemon actual constructor(
    private val coroutineScope: CoroutineScope,
    private val coordinator: WorkChainCoordinator
) {
    private var loopJob: Job? = null

    actual fun start() {
        loopJob = coroutineScope.launch {
            coordinator.sweepOrphans()
            coordinator.sweepChainRecovery()
            poll()
        }
    }

    private suspend fun poll() {
        while (coroutineScope.isActive) {
            try {
                coordinator.runPendingTasks()
            } catch (e: Exception) {
                Log.e("ChronosDaemon", "poll failed", e)
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    actual fun stop() {
        loopJob?.cancel()
        loopJob = null
    }
}