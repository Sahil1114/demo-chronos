package io.github.sahilmakkar.chronos.utils

import kotlinx.datetime.Instant
import kotlin.time.Clock


/**
 * Interface representing a platform-independent monotonic or wall-clock.
 */
interface KmpClock {
    /**
     * Returns the current date-time instant.
     */
    fun now(): Instant
}

/**
 * Standard implementation of [KmpClock] that uses Kotlinx DateTime's system clock.
 */
object SystemClock : KmpClock {
    override fun now(): Instant = Clock.System.now()
}
