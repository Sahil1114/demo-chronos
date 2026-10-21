package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.port.TaskCanceller
import io.github.sahilmakkar.chronos.port.TaskEnqueuer
import io.github.sahilmakkar.chronos.port.TaskObserver
import io.github.sahilmakkar.chronos.task.TaskChain
import io.github.sahilmakkar.chronos.task.request.TaskRequest
import io.github.sahilmakkar.chronos.worker.WorkerRegistry

interface Chronos :
    TaskEnqueuer,
    TaskCanceller,
    TaskObserver,
    AutoCloseable {

    suspend fun runPendingTasksNow()

    val registry: WorkerRegistry

    companion object
}

expect fun Chronos.Companion.initialize(support: PlatformSupport, registry: WorkerRegistry): Chronos

val Chronos.Companion.instance: Chronos
    get() = ChronosRegistry.instance

fun Chronos.Companion.shutdown() = ChronosRegistry.clear()

fun Chronos.Companion.setForTesting(instance: Chronos) = ChronosRegistry.set(instance)

fun Chronos.beginWith(request: TaskRequest): TaskChain = TaskChain.start(request)
