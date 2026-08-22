package io.github.sahilmakkar.chronos.network

/**
 * Interface for checking active network connectivity and characteristics.
 * Platform targets (Android, iOS, JVM) implement this to satisfy [io.github.sahilmakkar.chronos.task.constraints.EnvironmentConstraints.requiredNetworkType].
 */
interface NetworkChecker {
    /**
     * Checks if the device is currently connected to any network.
     */
    fun isConnected(): Boolean

    /**
     * Checks if the active connection is unmetered (i.e. does not charge/count data against caps).
     */
    fun isUnmetered(): Boolean
}