package io.github.sahilmakkar.chronos.executor

import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.sahilmakkar.chronos.AndroidDelegatingWorker
import io.github.sahilmakkar.chronos.mapper.WorkManagerConstraintsMapper.toWorkManagerConstraints
import io.github.sahilmakkar.chronos.port.TaskExecutor
import io.github.sahilmakkar.chronos.storage.ChronosDatabase
import io.github.sahilmakkar.chronos.storage.TaskEntity
import io.github.sahilmakkar.chronos.task.TaskResult
import io.github.sahilmakkar.chronos.task.TaskState
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.fromJson
import io.github.sahilmakkar.chronos.task.model.taskDataOf
import io.github.sahilmakkar.chronos.task.model.toJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import java.util.UUID

class AndroidTaskExecutor(
    private val workManager: WorkManager,
    private val db: ChronosDatabase
) : TaskExecutor {

    override suspend fun execute(entity: TaskEntity, inputData: TaskData): TaskResult {
        db.taskDao().updateTaskState(id = entity.id, state = TaskState.RUNNING)
        db.taskDao().updateRunAttemptCount(id = entity.id, count = entity.runAttemptCount + 1)

        val workId = UUID.fromString(entity.id)
        val request = OneTimeWorkRequestBuilder<AndroidDelegatingWorker>()
            .setId(workId)
            .setInputData(
                Data.Builder()
                    .putString("worker_class_name", entity.workerClassName)
                    .putString("input_data_json", inputData.toJson())
                    .build()
            )
            .setConstraints(entity.toWorkManagerConstraints())
            // No setBackoffCriteria — WorkManager must never auto-retry.
            // Retry/backoff timing is fully owned by TaskCoordinator.
            .build()

        return try {
            workManager.enqueue(request)

            workManager.getWorkInfoByIdFlow(workId)
                .mapNotNull { info -> info?.state?.let { toTaskResult(it, info) } }
                .first()
        } catch (e: Exception) {
            TaskResult.Failure(
                outputData = taskDataOf("error" to (e.message ?: "WorkManager dispatch failed"))
            )
        }
    }

    private fun toTaskResult(state: WorkInfo.State, info: WorkInfo): TaskResult? = when (state) {
        WorkInfo.State.SUCCEEDED -> TaskResult.Success(
            outputData = TaskData.fromJson(info.outputData.getString("output_data_json") ?: "{}")
        )

        WorkInfo.State.FAILED -> {
            // AndroidDelegatingWorker signals "please retry" by tagging the
            // failure output rather than calling Result.retry(), so the
            // coordinator's own retry/backoff logic stays authoritative.
            val output = info.outputData
            if (output.getBoolean("retry_requested", false)) {
                TaskResult.Retry
            } else {
                TaskResult.Failure(
                    outputData = TaskData.fromJson(output.getString("output_data_json") ?: "{}")
                )
            }
        }

        WorkInfo.State.CANCELLED -> TaskResult.Failure(
            outputData = taskDataOf("error" to "Task was cancelled by WorkManager")
        )

        else -> null // ENQUEUED, RUNNING, BLOCKED — keep observing
    }
}
