package io.github.sahilmakkar.chronos.scheduler.canceller

expect class ChronosWorkCanceller : WorkCanceller {
    override fun cancelWorkById(id: String)
    override fun cancelWorkByTag(tag: String)
    override fun cancelAllWork()
}