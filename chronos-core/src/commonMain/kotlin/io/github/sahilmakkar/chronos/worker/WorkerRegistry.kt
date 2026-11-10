package io.github.sahilmakkar.chronos.worker

import io.github.sahilmakkar.chronos.task.model.TaskData

/**
 * Thread-safe registry that maps fully-qualified worker class names to factory lambdas.
 *
 * In Kotlin Multiplatform environments (such as Kotlin/Native on iOS), dynamic class reflection
 * is either constrained or unavailable. Chronos avoids runtime reflection by requiring workers
 * to be registered as explicit factory lambdas within a [WorkerRegistry].
 *
 * ### Automatic Registration with KSP:
 * By annotating worker classes with [io.github.sahilmakkar.chronos.RegisterWorker], the Chronos KSP processor
 * (`chronos-processor`) generates an extension function:
 * ```kotlin
 * val registry = WorkerRegistry().apply {
 *     registerGeneratedWorkers()
 * }
 * ```
 *
 * ### Manual Registration:
 * Workers may also be registered manually:
 * ```kotlin
 * val registry = WorkerRegistry().apply {
 *     register("com.example.SyncWorker") { input -> SyncWorker(input) }
 * }
 * ```
 *
 * @see TaskWorker
 * @see io.github.sahilmakkar.chronos.RegisterWorker
 */
public class WorkerRegistry {
    private val factories = mutableMapOf<String, (TaskData) -> TaskWorker>()

    /**
     * Registers a factory lambda for the given worker class name.
     *
     * @param className Fully-qualified name of the worker class.
     * @param factory Lambda constructing a [TaskWorker] given input [TaskData].
     */
    public fun register(className: String, factory: (TaskData) -> TaskWorker) {
        factories[className] = factory
    }

    /**
     * Creates a worker instance for the registered class name with the provided input data.
     *
     * @param className Fully-qualified name of the worker class.
     * @param inputData Input parameters delivered to the constructed worker.
     * @return The newly instantiated [TaskWorker].
     * @throws IllegalArgumentException If no factory is registered for [className].
     */
    public fun createWorker(className: String, inputData: TaskData): TaskWorker {
        val factory = factories[className] ?: throw IllegalArgumentException(
            "Worker class $className is not registered. Ensure it is annotated with @RegisterWorker or registered manually."
        )
        return factory(inputData)
    }
}
