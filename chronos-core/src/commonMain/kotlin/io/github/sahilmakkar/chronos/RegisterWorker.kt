package io.github.sahilmakkar.chronos

import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class RegisterWorker(val factory: KClass<*> = Any::class)