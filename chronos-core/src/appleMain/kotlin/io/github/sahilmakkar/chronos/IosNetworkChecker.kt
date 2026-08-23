package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.network.NetworkChecker
import platform.Network.*
import platform.darwin.dispatch_get_main_queue

/**
 * Apple iOS implementation of [io.github.sahilmakkar.chronos.network.NetworkChecker] that uses native Network framework APIs
 * ([nw_path_monitor_t]) to observe cellular/Wi-Fi connection states.
 */
class IosNetworkChecker : NetworkChecker {
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

    override fun isConnected(): Boolean = connected
    override fun isUnmetered(): Boolean = !expensive
}
