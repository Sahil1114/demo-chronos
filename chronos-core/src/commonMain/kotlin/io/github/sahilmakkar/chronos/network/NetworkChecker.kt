package io.github.sahilmakkar.chronos.network

interface NetworkChecker {

    fun isConnected(): Boolean

    fun isUnmetered(): Boolean

    fun satisfies(required: NetworkType): Boolean = when (required) {
        NetworkType.NOT_REQUIRED -> true
        NetworkType.CONNECTED    -> isConnected()
        NetworkType.UNMETERED    -> isConnected() && isUnmetered()
    }
}