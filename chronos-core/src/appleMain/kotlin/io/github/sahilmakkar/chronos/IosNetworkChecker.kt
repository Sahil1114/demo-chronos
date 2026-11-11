package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.port.NetworkChecker
import platform.Network.*
import platform.darwin.dispatch_get_main_queue

/**
 * Apple/iOS network connectivity checker evaluating path reachability via Network framework.
 *
 * Utilizes `nw_path_monitor_t` running on the main dispatch queue to observe network path transitions:
 * - [isConnected]: Evaluates whether `nw_path_get_status` is `nw_path_status_satisfied`.
 * - [isUnmetered]: Evaluates whether the connection is satisfied and `nw_path_is_expensive` is false
 *   (i.e. not cellular data or personal hotspot).
 */
internal class IosNetworkChecker : NetworkChecker {
    private var connected = true
    private var expensive = false
    private val monitor = nw_path_monitor_create()

    init {
        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_set_update_handler(monitor) { path ->
            connected = nw_path_get_status(path) == nw_path_status_satisfied
            expensive = nw_path_is_expensive(path)
        }
        nw_path_monitor_start(monitor)
    }

    /**
     * Checks if the Apple Network framework path status is currently satisfied.
     *
     * @return `true` if connected to an active network interface; `false` otherwise.
     */
    override fun isConnected(): Boolean = connected

    /**
     * Checks if the current network path is active and not flagged as expensive (cellular/metered).
     *
     * @return `true` if unmetered; `false` otherwise.
     */
    override fun isUnmetered(): Boolean = connected && !expensive
}
