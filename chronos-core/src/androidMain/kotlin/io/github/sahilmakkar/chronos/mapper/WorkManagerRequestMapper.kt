package io.github.sahilmakkar.chronos.mapper

import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkRequest
import io.github.sahilmakkar.chronos.AndroidDelegatingWorker
import io.github.sahilmakkar.chronos.mapper.WorkManagerConstraintsMapper.toConstraints
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import io.github.sahilmakkar.chronos.task.request.PeriodicTaskRequest
import io.github.sahilmakkar.chronos.task.request.TaskRequest
import java.util.UUID
import java.util.concurrent.TimeUnit

internal object WorkManagerRequestMapper {

    fun toWorkRequest(request: TaskRequest): WorkRequest = when (request) {
        is OneTimeTaskRequest -> toOneTimeWorkRequest(request)
        is PeriodicTaskRequest -> toPeriodicWorkRequest(request)
    }

    fun toOneTimeWorkRequest(request: OneTimeTaskRequest): OneTimeWorkRequest {
        return OneTimeWorkRequestBuilder<AndroidDelegatingWorker>()
            .applyCommon(request)
            .build()
    }

    fun toPeriodicWorkRequest(request: PeriodicTaskRequest): PeriodicWorkRequest =
        PeriodicWorkRequestBuilder<AndroidDelegatingWorker>(
            repeatInterval = request.repeatInterval.inWholeMilliseconds,
            repeatIntervalTimeUnit = TimeUnit.MILLISECONDS
        )
            .applyCommon(request)
            .build()

    private fun <W : WorkRequest, B : WorkRequest.Builder<B, W>>
            WorkRequest.Builder<B, W>.applyCommon(
        request: TaskRequest
    ): B {
        val workRequest: B = (this as B)
            .setId(UUID.fromString(request.id))
            .setInputData(WorkManagerDataMapper.toData(request.taskData, request.workerClassName))
            .setConstraints(request.constraints.toConstraints())
            .setInitialDelay(request.initialDelay.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(
                backoffPolicy = request.backoffPolicy.toNative(),
                backoffDelay = request.backoffDelay.inWholeMilliseconds,
                timeUnit = TimeUnit.MILLISECONDS
            )
        request.tags.forEach { workRequest.addTag(it) }
        return workRequest
    }
}

@Deprecated("Use WorkManagerRequestMapper instead", ReplaceWith("WorkManagerRequestMapper"))
internal typealias WorkRequestMapper = WorkManagerRequestMapper
