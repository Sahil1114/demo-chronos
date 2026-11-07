package io.github.sahilmakkar.chronos.worker

import io.github.sahilmakkar.chronos.task.model.TaskData

/**
 * Registry holding factory functions for instantiating [TaskWorker] implementations by class name.
 */
public class WorkerRegistry {
    private val factories = mutableMapOf<String, (TaskData) -> TaskWorker>()

    /**
     * Registers a factory lambda for the given worker class name.
     */
    public fun register(className: String, factory: (TaskData) -> TaskWorker) {
        factories[className] = factory
    }

    /**
     * Creates a worker instance for the registered class name with the provided input data.
     */
    public fun createWorker(className: String, inputData: TaskData): TaskWorker {
        val factory = factories[className] ?: throw IllegalArgumentException(
            "Worker class $className is not registered. Ensure it is annotated with @RegisterWorker or registered manually."
        )
        return factory(inputData)
    }
}
