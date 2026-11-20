package io.github.sahilmakkar.chronos.engine

import io.github.sahilmakkar.chronos.scheduler.IosWakeScheduler
import io.github.sahilmakkar.chronos.storage.ChronosDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Apple/iOS background daemon managing task scheduling loops and Apple `BGTaskScheduler` (via [IosWakeScheduler]) lifecycle integration.
 */
internal actual class Daemon actual constructor(
    private val coroutineScope: CoroutineScope,
    private val coordinator: TaskCoordinator,
    private val db: ChronosDatabase,
    private val clock: Clock,
) {
    private lateinit var scheduler: IosWakeScheduler
    private val dueTimeLoop = DueTimeLoop(db = db, coordinator = coordinator, clock = clock)

    constructor(
        coroutineScope: CoroutineScope,
        coordinator: TaskCoordinator,
        db: ChronosDatabase,
        scheduler: IosWakeScheduler,
        clock: Clock = Clock.System,
    ) : this(
        coroutineScope = coroutineScope,
        coordinator = coordinator,
        db = db,
        clock = clock,
    ) {
        this.scheduler = scheduler
    }

    private fun requireScheduler() =
        if (::scheduler.isInitialized) {
            scheduler
        } else {
            error("Daemon.start()/stop() called without constructing via the scheduler constructor")
        }

    actual fun start() {
        requireScheduler().register()
        coroutineScope.launch {
            coordinator.sweepOrphans()
            coordinator.sweepChainRecovery()
            requireScheduler().scheduleNextWake()
        }
        dueTimeLoop.start(coroutineScope = coroutineScope)
    }

    actual fun stop() {
        dueTimeLoop.stop()
        requireScheduler().cancelWake()
    }
}
