package io.github.sahilmakkar.chronos

import android.content.Context
import androidx.startup.Initializer

internal object ChronosContextHolder {
    lateinit var appContext: Context
        private set

    fun init(context: Context) {
        if (!::appContext.isInitialized) {
            appContext = context.applicationContext
        }
    }
}
private class ChronosInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        ChronosContextHolder.init(context)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}