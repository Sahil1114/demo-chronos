package io.github.sahilmakkar.chronos.port

internal interface TaskCanceller {
    fun cancelTaskById(id: String)
    fun cancelTaskByTag(tag: String)
    fun cancelAllTasks()
}
