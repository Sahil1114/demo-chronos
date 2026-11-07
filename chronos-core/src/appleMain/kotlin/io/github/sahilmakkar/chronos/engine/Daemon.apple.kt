package io.github.sahilmakkar.chronos.engine

import io.github.sahilmakkar.chronos.executor.IosBackgroundTaskScheduler
import io.github.sahilmakkar.chronos.storage.ChronosDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Apple/iOS background daemon managing task scheduling loops and BGTaskScheduler integration.
 */
internal actual class Daemon actual constructor(
    private val coroutineScope: CoroutineScope,
    private val coordinator: TaskCoordinator,
    private val db: ChronosDatabase,
    private val clock: Clock
) {
    private lateinit var scheduler: IosBackgroundTaskScheduler
    private val dueTimeLoop = DueTimeLoop(db, coordinator, clock)

    constructor(
        coroutineScope: CoroutineScope,
        coordinator: TaskCoordinator,
        db: ChronosDatabase,
        scheduler: IosBackgroundTaskScheduler,
        clock: Clock = Clock.System
    ) : this(coroutineScope, coordinator, db, clock) {
        this.scheduler = scheduler
    }

    private fun requireScheduler() =
        if (::scheduler.isInitialized) scheduler
        else error("Daemon.start()/stop() called without constructing via the scheduler constructor")

    actual fun start() {
        coroutineScope.launch {
            coordinator.sweepOrphans()
            coordinator.sweepChainRecovery()
        }
        dueTimeLoop.start(coroutineScope)
        requireScheduler().register()
    }

    actual fun stop() {
        dueTimeLoop.stop()
        requireScheduler().cancelScheduled()
    }
}
