package io.github.sahilmakkar.chronos.processor

import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class RegisterWorker(val factory: KClass<*> = Any::class)