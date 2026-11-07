package io.github.sahilmakkar.chronos.scheduler

/**
 * Schedules background wake triggers on platforms that support OS background execution.
 */
internal interface WakeScheduler {
    suspend fun scheduleNextWake()
    fun cancelWake()
}
