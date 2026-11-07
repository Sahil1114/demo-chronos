package io.github.sahilmakkar.chronos.scheduler

internal interface WakeScheduler {
    suspend fun scheduleNextWake()
    fun cancelWake()
}
