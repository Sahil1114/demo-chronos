package io.github.sahilmakkar.chronos

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.github.sahilmakkar.chronos.network.NetworkChecker

/**
 * Android implementation of [io.github.sahilmakkar.chronos.network.NetworkChecker] that uses Android's [ConnectivityManager]
 * to determine cellular/Wi-Fi connectivity and metered vs. unmetered state.
 */
@SuppressLint("MissingPermission")
class AndroidNetworkChecker(private val context: Context) : NetworkChecker {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun isConnected(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun isUnmetered(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
