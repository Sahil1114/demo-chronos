package io.github.sahilmakkar.chronos.port

interface TaskCanceller {
    fun cancelTaskById(id: String)
    fun cancelTaskByTag(tag: String)
    fun cancelAllTasks()

    @Deprecated("Use cancelTaskById instead", ReplaceWith("cancelTaskById(id)"))
    fun cancelWorkById(id: String) = cancelTaskById(id)

    @Deprecated("Use cancelTaskByTag instead", ReplaceWith("cancelTaskByTag(tag)"))
    fun cancelWorkByTag(tag: String) = cancelTaskByTag(tag)

    @Deprecated("Use cancelAllTasks instead", ReplaceWith("cancelAllTasks()"))
    fun cancelAllWork() = cancelAllTasks()
}

@Deprecated("Use TaskCanceller instead", ReplaceWith("TaskCanceller"))
typealias WorkCanceller = TaskCanceller
