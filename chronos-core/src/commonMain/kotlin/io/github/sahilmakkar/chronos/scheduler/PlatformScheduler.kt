package io.github.sahilmakkar.chronos.scheduler

interface PlatformScheduler {
    suspend fun runPendingTasksNow()
}