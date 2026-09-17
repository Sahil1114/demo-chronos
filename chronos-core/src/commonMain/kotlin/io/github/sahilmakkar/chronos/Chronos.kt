package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.scheduler.PlatformScheduler
import io.github.sahilmakkar.chronos.scheduler.WorkObserver
import io.github.sahilmakkar.chronos.scheduler.canceller.WorkCanceller
import io.github.sahilmakkar.chronos.scheduler.workEnqueuer.WorkEnqueuer
import io.github.sahilmakkar.chronos.task.TaskChain
import io.github.sahilmakkar.chronos.task.request.TaskRequest
import io.github.sahilmakkar.chronos.worker.WorkerRegistry

interface Chronos :
    WorkEnqueuer,
    WorkCanceller,
    WorkObserver,
    PlatformScheduler,
    AutoCloseable
{

    val registry: WorkerRegistry

    companion object
}


expect fun Chronos.Companion.initialize(support: PlatformSupport, registry: WorkerRegistry): Chronos

val Chronos.Companion.instance: Chronos
    get() = ChronosRegistry.instance

fun Chronos.Companion.shutdown() = ChronosRegistry.clear()

fun Chronos.Companion.setForTesting(instance: Chronos) = ChronosRegistry.set(instance)

fun Chronos.beginWith(request: TaskRequest): TaskChain = TaskChain.start(request)