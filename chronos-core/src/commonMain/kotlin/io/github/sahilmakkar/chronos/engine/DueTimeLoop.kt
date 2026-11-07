package io.github.sahilmakkar.chronos.engine

import io.github.sahilmakkar.chronos.storage.ChronosDatabase
import io.github.sahilmakkar.chronos.task.TaskState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Event loop that monitors the database for the next due task and triggers execution passes.
 */
internal class DueTimeLoop(
    private val db: ChronosDatabase,
    private val coordinator: TaskCoordinator,
    private val clock: Clock = Clock.System,
    private val blockedRecheckInterval: Duration = 15.seconds
) {
    private var loopJob: Job? = null

    fun start(coroutineScope: CoroutineScope) {
        val wakeChannel = Channel<Unit>(Channel.CONFLATED)

        loopJob = coroutineScope.launch {
            val states = listOf(TaskState.ENQUEUED, TaskState.RETRYING)

            launch {
                for (unit in wakeChannel) {
                    coordinator.runPendingTasks()
                }
            }

            try {
                db.taskDao().observeNextDueAt(states).distinctUntilChanged().collectLatest { nextDueAt ->
                    if (nextDueAt == null) return@collectLatest

                    val nowMs = clock.now().toEpochMilliseconds()
                    val delayMs = nextDueAt - nowMs
                    if (delayMs > 0) {
                        delay(delayMs)
                    }

                    while (isActive) {
                        wakeChannel.send(Unit)
                        delay(blockedRecheckInterval)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
            }
        }
    }

    fun stop() {
        val job = loopJob
        loopJob = null
        job?.cancel()
    }

    suspend fun stopAndJoin() {
        val job = loopJob
        loopJob = null
        job?.cancelAndJoin()
    }
}
