package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.task.TaskChain
import io.github.sahilmakkar.chronos.task.request.TaskRequest

/**
 * Process-wide singleton registry holding the active [Chronos] instance.
 *
 * Manages atomic assignment, automatic closing of superseded instances, and thread-safe reset on shutdown.
 */
internal object ChronosRegistry {
    private var current: Chronos? = null

    val instance: Chronos
        get() = current ?: error("Chronos has not been initialized. Call Chronos.initialize(...) first.")

    fun set(instance: Chronos) {
        if (current === instance) return
        current?.close()
        current = instance
    }

    fun clear() {
        current?.close()
        current = null
    }
}
