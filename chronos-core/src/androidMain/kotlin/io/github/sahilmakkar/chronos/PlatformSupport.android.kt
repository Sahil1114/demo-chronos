package io.github.sahilmakkar.chronos

import android.content.Context

/**
 * Android platform bindings wrapping an Android [Context].
 *
 * It is recommended to pass an `Application` context to prevent activity context leaks across
 * background execution lifecycles.
 *
 * @property context Application context used for Room database creation, system service monitoring, and WorkManager wake scheduling.
 *
 * @see Chronos.Companion.initialize
 */
public actual class PlatformSupport(public val context: Context)
