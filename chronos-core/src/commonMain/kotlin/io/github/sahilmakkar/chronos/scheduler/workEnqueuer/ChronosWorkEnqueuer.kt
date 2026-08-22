package io.github.sahilmakkar.chronos.scheduler.workEnqueuer

import io.github.sahilmakkar.chronos.task.policy.UniqueTaskPolicy
import io.github.sahilmakkar.chronos.task.TaskChain
import io.github.sahilmakkar.chronos.task.request.OneTimeTaskRequest
import io.github.sahilmakkar.chronos.task.request.PeriodicTaskRequest
import io.github.sahilmakkar.chronos.task.request.TaskRequest

expect class ChronosWorkEnqueuer : WorkEnqueuer {
    override fun enqueue(request: TaskRequest)
    override fun enqueueUniqueWork(name: String, policy: UniqueTaskPolicy, request: OneTimeTaskRequest)
    override fun enqueueUniquePeriodicWork(name: String, policy: UniqueTaskPolicy, request: PeriodicTaskRequest)
    override fun enqueue(chain: TaskChain)
}