package io.github.sahilmakkar.chronos.mapper

import androidx.work.WorkInfo
import io.github.sahilmakkar.chronos.task.TaskState
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.TaskInfo
import io.github.sahilmakkar.chronos.task.model.fromJson

internal object WorkManagerInfoMapper {

    fun WorkInfo.toTaskInfo(): TaskInfo {
        val state = when (this.state) {
            WorkInfo.State.ENQUEUED -> TaskState.ENQUEUED
            WorkInfo.State.RUNNING -> TaskState.RUNNING
            WorkInfo.State.SUCCEEDED -> TaskState.SUCCEEDED
            WorkInfo.State.FAILED -> TaskState.FAILED
            WorkInfo.State.BLOCKED -> TaskState.ENQUEUED
            WorkInfo.State.CANCELLED -> TaskState.CANCELLED
        }
        return TaskInfo(
            id = this.id.toString(),
            state = state,
            outputData = TaskData.fromJson(this.outputData.getString("output_data_json") ?: "{}"),
            progress = TaskData.fromJson(this.progress.getString("progress_data_json") ?: "{}"),
            runAttemptCount = this.runAttemptCount
        )
    }

    @Deprecated("Use toTaskInfo instead", ReplaceWith("toTaskInfo()"))
    fun WorkInfo.toWorkInfo(): TaskInfo = toTaskInfo()
}

@Deprecated("Use WorkManagerInfoMapper instead", ReplaceWith("WorkManagerInfoMapper"))
internal typealias WorkInfoMapper = WorkManagerInfoMapper
