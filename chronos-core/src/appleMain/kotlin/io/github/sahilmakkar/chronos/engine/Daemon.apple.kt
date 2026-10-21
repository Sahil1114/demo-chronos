package io.github.sahilmakkar.chronos.engine

import io.github.sahilmakkar.chronos.executor.IosBackgroundTaskScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal actual class Daemon actual constructor(
    private val coroutineScope: CoroutineScope,
    private val coordinator: TaskCoordinator
) {
    private lateinit var scheduler: IosBackgroundTaskScheduler

    constructor(
        coroutineScope: CoroutineScope,
        coordinator: TaskCoordinator,
        scheduler: IosBackgroundTaskScheduler
    ) : this(coroutineScope, coordinator) {
        this.scheduler = scheduler
    }

    private fun requireScheduler() =
        if (::scheduler.isInitialized) scheduler
        else error("Daemon.start()/stop() called without constructing via the 3-arg constructor")

    actual fun start() {
        coroutineScope.launch {
            coordinator.sweepOrphans()
            coordinator.sweepChainRecovery()
        }
        requireScheduler().register()
    }

    actual fun stop() {
        requireScheduler().cancelScheduled()
    }
}
