package io.github.sahilmakkar.chronos.task.constraints

/**
 * Network state requirements for executing a task.
 */
public enum class NetworkType {
    /**
     * No network connection required.
     */
    NOT_REQUIRED,

    /**
     * Any working network connection required.
     */
    CONNECTED,

    /**
     * Unmetered (e.g. Wi-Fi) network connection required.
     */
    UNMETERED
}
