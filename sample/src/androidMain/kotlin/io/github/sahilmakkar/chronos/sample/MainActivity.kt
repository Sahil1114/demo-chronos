package io.github.sahilmakkar.chronos.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.sahilmakkar.chronos.Chronos

/**
 * Main Android activity that boots the sample Compose application and initializes Chronos.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val registry = createSampleRegistry()
        val chronos = Chronos(context = this, registry = registry)

        setContent {
            App(chronos)
        }
    }
}
