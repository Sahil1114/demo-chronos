package io.github.sahilmakkar.chronos

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.github.sahilmakkar.chronos.port.NetworkChecker

/**
 * Android implementation of [NetworkChecker] verifying active network capability and unmetered status.
 *
 * Interrogates Android's [ConnectivityManager] and queries [NetworkCapabilities] on the active network:
 * - [NetworkCapabilities.NET_CAPABILITY_INTERNET] to confirm outbound internet route reachability.
 * - [NetworkCapabilities.NET_CAPABILITY_NOT_METERED] to confirm unmetered Wi-Fi/Ethernet connectivity.
 *
 * @param context Android application context used to obtain [ConnectivityManager].
 */
@SuppressLint("MissingPermission")
internal class AndroidNetworkChecker(private val context: Context) : NetworkChecker {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Checks if the active network has the [NetworkCapabilities.NET_CAPABILITY_INTERNET] capability.
     *
     * @return `true` if connected to an internet-capable network; `false` otherwise.
     */
    override fun isConnected(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Checks if the active network has the [NetworkCapabilities.NET_CAPABILITY_NOT_METERED] capability.
     *
     * @return `true` if unmetered; `false` if metered or disconnected.
     */
    override fun isUnmetered(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
