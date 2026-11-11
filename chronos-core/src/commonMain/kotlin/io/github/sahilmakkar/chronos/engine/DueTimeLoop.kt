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
 * Reactive event loop that observes the earliest scheduled execution time in the database and triggers dispatch passes.
 *
 * Replaces high-frequency polling with a reactive, event-driven sleeping loop:
 * 1. Observes [io.github.sahilmakkar.chronos.storage.TaskDao.observeNextDueAt] via Room Flow.
 * 2. Whenever the earliest due time changes (e.g. a new task is inserted or retried), [collectLatest] cancels
 *    any pending sleep delay and recalibrates the wait duration.
 * 3. Delays exactly until `nextDueAt - nowMs`, then triggers an execution pass via a conflated wake channel.
 * 4. Runs a low-frequency fallback check ([blockedRecheckInterval]) to re-evaluate tasks previously blocked
 *    by unsatisfied hardware or network constraints.
 *
 * @param db Database instance queried for the next scheduled task.
 * @param coordinator Coordinator dispatched when a task becomes due.
 * @param clock Time source used for calculating sleep durations.
 * @param blockedRecheckInterval Fallback interval for re-evaluating constraint-blocked tasks (default 15 seconds).
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
