package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.network.NetworkChecker
import java.net.NetworkInterface

class JvmNetworkChecker : NetworkChecker {
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

    override fun isUnmetered(): Boolean {
        return isConnected()
    }
}
