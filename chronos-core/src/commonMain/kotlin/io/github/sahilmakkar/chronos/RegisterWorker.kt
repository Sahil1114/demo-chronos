package io.github.sahilmakkar.chronos

import kotlin.reflect.KClass

/**
 * Annotates a [io.github.sahilmakkar.chronos.worker.TaskWorker] class for automatic code generation and registration by KSP.
 *
 * During compilation, the `chronos-processor` scans for `@RegisterWorker` annotations and generates
 * the `WorkerRegistry.registerGeneratedWorkers()` extension function, eliminating the need to write
 * manual worker registration boilerplate or rely on runtime reflection.
 *
 * ### Example:
 * ```kotlin
 * @RegisterWorker
 * class DataCleanupWorker(input: TaskData) : TaskWorker(input) {
 *     override suspend fun doWork(): TaskResult {
 *         // execution logic
 *         return TaskResult.Success()
 *     }
 * }
 * ```
 *
 * @property factory Optional factory class for custom worker instantiation (e.g. dependency injection).
 * Defaults to `Any::class` indicating standard constructor instantiation `(TaskData) -> TaskWorker`.
 *
 * @see io.github.sahilmakkar.chronos.worker.WorkerRegistry
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class RegisterWorker(
    val factory: KClass<*> = Any::class
)
