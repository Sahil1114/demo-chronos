package io.github.sahilmakkar.chronos.task.constraints

/**
 * Network connectivity constraints required for task execution.
 *
 * Gating tasks on network availability prevents wasteful execution passes and failed network calls.
 *
 * @see EnvironmentConstraints
 */
public enum class NetworkType {
    /**
     * No network connection is required.
     *
     * The task is eligible to run whether the device is offline, on a cellular network, or connected to Wi-Fi.
     */
    NOT_REQUIRED,

    /**
     * Requires any active, functional data network connection.
     *
     * The task will execute over cellular data, Wi-Fi, Ethernet, or other working network interfaces.
     */
    CONNECTED,

    /**
     * Requires an unmetered network connection (typically Wi-Fi or unmetered Ethernet).
     *
     * On Android, this checks `NET_CAPABILITY_NOT_METERED`. On iOS and JVM, it checks for active Wi-Fi or wired connections.
     * Recommended for large file downloads, database synchronization, and heavy media uploads.
     */
    UNMETERED
}
