package io.github.sahilmakkar.chronos.kmpWorker

import io.github.sahilmakkar.chronos.task.model.TaskData

/**
 * Registry mapping worker class name string identifiers to their respective factory lambda functions.
 * Chronos platform implementations use this registry to instantiate workers dynamically when scheduling triggers.
 */
class KmpWorkerRegistry {
    private val factories = mutableMapOf<String, (TaskData) -> KmpWorker>()

    /**
     * Registers a worker factory for the given class name.
     *
     * @param className Fully-qualified name of the worker class.
     * @param factory Factory lambda creating a [KmpWorker] from the provided [TaskData].
     */
    fun register(className: String, factory: (TaskData) -> KmpWorker) {
        factories[className] = factory
    }

    /**
     * Instantiates a registered worker.
     *
     * @param className Fully-qualified class name of the worker to instantiate.
     * @param inputData Input parameters/configuration data for the worker.
     * @return The instantiated [KmpWorker] instance.
     * @throws IllegalArgumentException if the worker class name is not registered.
     */
    fun createWorker(className: String, inputData: TaskData): KmpWorker {
        val factory = factories[className] ?: throw IllegalArgumentException(
            "Worker class $className is not registered. Ensure it is annotated with @RegisterWorker or registered manually."
        )
        return factory(inputData)
    }
}
