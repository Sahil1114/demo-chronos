package io.github.sahilmakkar.chronos.kmpWorker

import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.TaskResult
import kotlin.reflect.KClass

/**
 * Annotation used to register a [KmpWorker] class with the code generation processor.
 *
 * Annotating a worker class with `@RegisterWorker` triggers the Chronos KSP processor
 * to generate registry extension functions automatically, avoiding manual reflection/registration setup.
 *
 * @property factory The worker factory class (optional, defaults to Any::class).
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class RegisterWorker(val factory: KClass<*> = Any::class)

/**
 * The base class for defining background tasks. Subclasses must implement [doWork]
 * to carry out the task.
 *
 * @property inputData Input data passed to this worker instance.
 */
abstract class KmpWorker(val inputData: TaskData) {

    /**
     * Executes the background task.
     * Override this function to perform the actual background execution logic.
     *
     * @return The [TaskResult] indicating whether the execution succeeded, failed, or needs to retry.
     */
    abstract suspend fun doWork(): TaskResult

    private var progressListener: (suspend (TaskData) -> Unit)? = null

    /**
     * Registers a listener to monitor progress updates published by this worker.
     */
    internal fun setProgressListener(listener: suspend (TaskData) -> Unit) {
        progressListener = listener
    }

    /**
     * Publishes intermediate progress for this worker execution.
     * The progress is persisted in the backing store and can be observed via work info flows.
     *
     * @param data The progress [TaskData] payload to save.
     */
    suspend fun setProgress(data: TaskData) {
        progressListener?.invoke(data)
    }
}

