package io.github.sahilmakkar.chronos.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.sahilmakkar.chronos.Chronos
import io.github.sahilmakkar.chronos.PlatformSupport
import io.github.sahilmakkar.chronos.initialize

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val registry = createSampleRegistry()
        val chronos = Chronos.initialize(
            support = PlatformSupport(context = applicationContext),
            registry = registry
        )

        setContent {
            App(chronos)
        }
    }
}