package io.github.sahilmakkar.chronos.sample

import android.app.Application
import io.github.sahilmakkar.chronos.Chronos
import io.github.sahilmakkar.chronos.PlatformSupport
import io.github.sahilmakkar.chronos.initialize

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Chronos.initialize(
            support = PlatformSupport(context = this),
            registry = createSampleRegistry()
        )
    }
}
