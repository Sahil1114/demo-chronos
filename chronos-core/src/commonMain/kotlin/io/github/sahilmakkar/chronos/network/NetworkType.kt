package io.github.sahilmakkar.chronos.network

/**
 * Specifies the network constraint required for a worker to execute.
 */
enum class NetworkType {
    /**
     * Network is not required for execution (default).
     */
    NOT_REQUIRED,

    /**
     * Any active network connection (cellular, Wi-Fi, etc.) is sufficient.
     */
    CONNECTED,

    /**
     * An unmetered connection (e.g., Wi-Fi without data caps) is required.
     */
    UNMETERED
}