package io.github.sahilmakkar.chronos.mapper

import androidx.work.WorkInfo
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.data.mapper.fromJson
import io.github.sahilmakkar.chronos.task.model.TaskInfo
import io.github.sahilmakkar.chronos.task.TaskState

internal object WorkInfoMapper {

    fun WorkInfo.toWorkInfo(): TaskInfo {
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
}