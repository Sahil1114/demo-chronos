package io.github.sahilmakkar.chronos.testing

import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Controllable [Clock] implementation for tests.
 *
 * @property nowMs Current epoch millisecond timestamp.
 */
public class FakeClock(public var nowMs: Long = 1000L) : Clock {

    public override fun now(): Instant = Instant.fromEpochMilliseconds(nowMs)

    /**
     * Advances the clock forward by the given number of milliseconds.
     */
    public fun advance(ms: Long) {
        nowMs += ms
    }
}
