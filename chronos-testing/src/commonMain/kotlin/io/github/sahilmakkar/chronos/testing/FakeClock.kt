package io.github.sahilmakkar.chronos.testing

import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Controllable, deterministic [Clock] test implementation for verifying time-sensitive workflows.
 *
 * Allows test suites to simulate the passage of wall-clock time instantaneously without relying on
 * real-time sleeping (e.g. `delay()` or `Thread.sleep()`). Commonly used to test:
 * - Task initial delays ([io.github.sahilmakkar.chronos.task.request.TaskRequest.initialDelay])
 * - Periodic task repeat intervals ([io.github.sahilmakkar.chronos.task.request.PeriodicTaskRequest.repeatInterval])
 * - Exponential and linear backoff retry delays ([io.github.sahilmakkar.chronos.task.policy.BackoffPolicy])
 * - Daily execution window boundaries ([io.github.sahilmakkar.chronos.task.constraints.TimeWindow])
 *
 * ### Usage Example
 * ```kotlin
 * val clock = FakeClock(nowMs = 1_000_000L)
 * val verifier = AndroidConstraintVerifier(context = appContext, clock = clock)
 *
 * // Fast-forward time by 2 hours
 * clock.advance(2.hours.inWholeMilliseconds)
 * assertEquals(Instant.fromEpochMilliseconds(1_000_000L + 7_200_000L), clock.now())
 * ```
 *
 * @property nowMs Current epoch timestamp in milliseconds. Defaults to `1000L`.
 */
public class FakeClock(public var nowMs: Long = 1000L) : Clock {

    /**
     * Returns the current virtual [Instant] derived from [nowMs].
     *
     * @return Current virtual timestamp.
     */
    public override fun now(): Instant = Instant.fromEpochMilliseconds(nowMs)

    /**
     * Advances the virtual clock forward by the specified number of milliseconds.
     *
     * @param ms Milliseconds to add to [nowMs].
     */
    public fun advance(ms: Long) {
        nowMs += ms
    }
}
