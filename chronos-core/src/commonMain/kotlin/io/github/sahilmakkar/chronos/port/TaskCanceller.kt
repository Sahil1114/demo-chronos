package io.github.sahilmakkar.chronos.port

/**
 * Port contract for task cancellation operations.
 */
internal interface TaskCanceller {
    fun cancelTaskById(id: String)
    fun cancelTaskByTag(tag: String)
    fun cancelAllTasks()
}
