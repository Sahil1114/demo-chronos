package io.github.sahilmakkar.chronos.worker

import io.github.sahilmakkar.chronos.task.model.TaskData

class WorkerRegistry {
    private val factories = mutableMapOf<String, (TaskData) -> Worker>()

    fun register(className: String, factory: (TaskData) -> Worker) {
        factories[className] = factory
    }

    fun createWorker(className: String, inputData: TaskData): Worker {
        val factory = factories[className] ?: throw IllegalArgumentException(
            "Worker class $className is not registered. Ensure it is annotated with @RegisterWorker or registered manually."
        )
        return factory(inputData)
    }
}
