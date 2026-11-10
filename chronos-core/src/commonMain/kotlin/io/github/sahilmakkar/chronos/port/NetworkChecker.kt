package io.github.sahilmakkar.chronos.port

import io.github.sahilmakkar.chronos.task.constraints.NetworkType

/**
 * Internal architecture port defining platform-specific network connectivity checks.
 *
 * Provides real-time network status introspection to evaluate whether a device possesses
 * active internet access and whether the active connection is unmetered (such as Wi-Fi or Ethernet)
 * versus metered (such as cellular data or metered hotspots).
 */
internal interface NetworkChecker {

    /**
     * Checks whether an active, functional data network connection is available on the device.
     *
     * @return `true` if the device has an active network capability with general internet access;
     *   `false` if disconnected or in airplane mode.
     */
    fun isConnected(): Boolean

    /**
     * Checks whether the active network connection is unmetered.
     *
     * Unmetered networks generally refer to Wi-Fi, Ethernet, or cellular plans where data usage
     * does not incur billing penalties or quota depletion.
     *
     * @return `true` if connected to an unmetered network; `false` if metered or disconnected.
     */
    fun isUnmetered(): Boolean

    /**
     * Evaluates whether the current network environment satisfies the given [required] constraint.
     *
     * - [NetworkType.NOT_REQUIRED]: Always returns `true`.
     * - [NetworkType.CONNECTED]: Returns `true` if [isConnected] is `true`.
     * - [NetworkType.UNMETERED]: Returns `true` only if both [isConnected] and [isUnmetered] are `true`.
     *
     * @param required The required network constraint specified on the task.
     * @return `true` if the current network condition satisfies [required]; `false` otherwise.
     */
    fun satisfies(required: NetworkType): Boolean = when (required) {
        NetworkType.NOT_REQUIRED -> true
        NetworkType.CONNECTED -> isConnected()
        NetworkType.UNMETERED -> isConnected() && isUnmetered()
    }
}
