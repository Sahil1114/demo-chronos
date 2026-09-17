package io.github.sahilmakkar.chronos.worker

import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.model.TaskData

abstract class Worker(val inputData: TaskData) {

    abstract suspend fun doWork(): TaskResult

    private var progressListener: (suspend (TaskData) -> Unit)? = null

    internal fun setProgressListener(listener: suspend (TaskData) -> Unit) {
        progressListener = listener
    }

    suspend fun setProgress(data: TaskData) {
        progressListener?.invoke(data)
    }
}

