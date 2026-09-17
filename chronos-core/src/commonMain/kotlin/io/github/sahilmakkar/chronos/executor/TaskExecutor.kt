package io.github.sahilmakkar.chronos.db.executor


import io.github.sahilmakkar.chronos.roomDb.WorkRequestEntity
import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.model.TaskData

interface TaskExecutor {
    suspend fun execute(entity: WorkRequestEntity, inputData: TaskData): TaskResult
}

expect class WorkConstraintVerifier{
    fun isSatisfied(entity: WorkRequestEntity): Boolean
}