package io.github.sahilmakkar.chronos.port

import io.github.sahilmakkar.chronos.task.constraints.NetworkType

/**
 * Queries current device network connectivity and metered status.
 */
internal interface NetworkChecker {
    fun isConnected(): Boolean
    fun isUnmetered(): Boolean
    fun satisfies(required: NetworkType): Boolean = when (required) {
        NetworkType.NOT_REQUIRED -> true
        NetworkType.CONNECTED -> isConnected()
        NetworkType.UNMETERED -> isConnected() && isUnmetered()
    }
}
