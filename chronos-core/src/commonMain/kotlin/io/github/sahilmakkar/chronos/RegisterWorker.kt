package io.github.sahilmakkar.chronos

import kotlin.reflect.KClass

/**
 * Annotates a [io.github.sahilmakkar.chronos.worker.TaskWorker] class for automatic code generation and registration.
 *
 * @property factory Optional factory class for creating instances of the annotated worker.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class RegisterWorker(
    val factory: KClass<*> = Any::class
)
