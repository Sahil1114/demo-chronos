package io.github.sahilmakkar.chronos

import android.content.Context

/**
 * Android platform support wrapping application [Context].
 *
 * @property context Application context used for database creation, system services, and WorkManager.
 */
public actual class PlatformSupport(public val context: Context)
