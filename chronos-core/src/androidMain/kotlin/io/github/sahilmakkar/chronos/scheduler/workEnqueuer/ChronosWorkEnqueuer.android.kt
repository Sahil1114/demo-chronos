package io.github.sahilmakkar.chronos.scheduler.workEnqueuer

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import io.github.sahilmakkar.chronos.task.policy.UniqueTaskPolicy
import io.github.sahilmakkar.chronos.task.TaskChain
import io.github.sahilmakkar.chronos.mapper.WorkRequestMapper
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import io.github.sahilmakkar.chronos.task.request.PeriodicTaskRequest
import io.github.sahilmakkar.chronos.task.request.TaskRequest


actual class ChronosWorkEnqueuer(
    private val workManager: WorkManager
) : WorkEnqueuer {

    actual override fun enqueue(request: TaskRequest) {
        with(WorkRequestMapper) {
            workManager.enqueue(toWorkRequest(request))
        }
    }

    actual override fun enqueueUniqueWork(
        name: String,
        policy: UniqueTaskPolicy,
        request: OneTimeTaskRequest
    ) {
        val nativePolicy = when (policy) {
            UniqueTaskPolicy.KEEP -> ExistingWorkPolicy.KEEP
            UniqueTaskPolicy.REPLACE -> ExistingWorkPolicy.REPLACE
            UniqueTaskPolicy.APPEND -> ExistingWorkPolicy.APPEND
        }
        with(WorkRequestMapper) {
            workManager.enqueueUniqueWork(
                uniqueWorkName = name,
                existingWorkPolicy = nativePolicy,
                request = toOneTimeWorkRequest(request)
            )
        }
    }

    actual override fun enqueueUniquePeriodicWork(
        name: String,
        policy: UniqueTaskPolicy,
        request: PeriodicTaskRequest
    ) {
        val nativePolicy = when (policy) {
            UniqueTaskPolicy.KEEP -> ExistingPeriodicWorkPolicy.KEEP
            UniqueTaskPolicy.REPLACE -> ExistingPeriodicWorkPolicy.REPLACE
            UniqueTaskPolicy.APPEND -> throw IllegalArgumentException(
                "APPEND policy is not supported for unique periodic work on Android."
            )
        }
        with(WorkRequestMapper) {
            workManager.enqueueUniquePeriodicWork(
                uniqueWorkName = name,
                existingPeriodicWorkPolicy = nativePolicy,
                request = toPeriodicWorkRequest(request)
            )
        }
    }

    actual override fun enqueue(chain: TaskChain) {
        if (chain.requests.isEmpty()) return
        with(WorkRequestMapper) {
            var continuation = workManager.beginWith(
                toOneTimeWorkRequest(chain.requests.first() as OneTimeTaskRequest)
            )
            for (i in 1 until chain.requests.size) {
                continuation = continuation.then(
                    toOneTimeWorkRequest(chain.requests[i] as OneTimeTaskRequest)
                )
            }
            continuation.enqueue()
        }
    }
}