package io.github.sahilmakkar.chronos.mapper

import androidx.work.Data
import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.toJson

internal object WorkManagerDataMapper {
    fun toData(workData: TaskData, workerClassName: String): Data =
        Data.Builder()
            .putString("worker_class_name", workerClassName)
            .putString("input_data_json", workData.toJson())
            .build()
}

@Deprecated("Use WorkManagerDataMapper instead", ReplaceWith("WorkManagerDataMapper"))
internal typealias WorkDataMapper = WorkManagerDataMapper
