package io.github.sahilmakkar.chronos.scheduler.canceller

/** Cancels scheduled or running work. */
interface WorkCanceller {
    fun cancelWorkById(id: String)
    fun cancelWorkByTag(tag: String)
    fun cancelAllWork()
}