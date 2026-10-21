package io.github.sahilmakkar.chronos.port

import io.github.sahilmakkar.chronos.task.TaskChain
import io.github.sahilmakkar.chronos.task.policy.UniqueTaskPolicy
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import io.github.sahilmakkar.chronos.task.request.PeriodicTaskRequest
import io.github.sahilmakkar.chronos.task.request.TaskRequest

/** Schedules new tasks. */
interface TaskEnqueuer {
    fun enqueue(request: TaskRequest)
    fun enqueueUniqueTask(name: String, policy: UniqueTaskPolicy, request: OneTimeTaskRequest)
    fun enqueueUniquePeriodicTask(name: String, policy: UniqueTaskPolicy, request: PeriodicTaskRequest)
    fun enqueue(chain: TaskChain)

    @Deprecated("Use enqueueUniqueTask instead", ReplaceWith("enqueueUniqueTask(name, policy, request)"))
    fun enqueueUniqueWork(name: String, policy: UniqueTaskPolicy, request: OneTimeTaskRequest) =
        enqueueUniqueTask(name, policy, request)

    @Deprecated("Use enqueueUniquePeriodicTask instead", ReplaceWith("enqueueUniquePeriodicTask(name, policy, request)"))
    fun enqueueUniquePeriodicWork(name: String, policy: UniqueTaskPolicy, request: PeriodicTaskRequest) =
        enqueueUniquePeriodicTask(name, policy, request)
}

@Deprecated("Use TaskEnqueuer instead", ReplaceWith("TaskEnqueuer"))
typealias WorkEnqueuer = TaskEnqueuer
