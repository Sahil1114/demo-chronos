package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.port.NetworkChecker
import java.net.NetworkInterface

/**
 * JVM network connectivity checker using standard Java network interfaces.
 *
 * Iterates through system [NetworkInterface] instances to verify if any non-loopback,
 * non-virtual interface is currently up and operational.
 */
internal class JvmNetworkChecker : NetworkChecker {

    /**
     * Inspects host [NetworkInterface.getNetworkInterfaces] for an active, non-loopback network interface.
     *
     * @return `true` if at least one active network interface is detected; `false` otherwise.
     */
    override fun isConnected(): Boolean {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            var hasActive = false
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isUp && !iface.isLoopback && !iface.isVirtual) {
                    hasActive = true
                    break
                }
            }
            hasActive
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if the active network interface is unmetered.
     *
     * In standard desktop/server JVM environments, network metering is not explicitly flagged by the OS,
     * so any active connection is treated as unmetered.
     *
     * @return `true` if connected; `false` otherwise.
     */
    override fun isUnmetered(): Boolean {
        return isConnected()
    }
}
