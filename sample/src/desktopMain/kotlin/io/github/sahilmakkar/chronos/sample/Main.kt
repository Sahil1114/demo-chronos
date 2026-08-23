package io.github.sahilmakkar.chronos.sample

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.sahilmakkar.chronos.Chronos

/**
 * Main desktop application entry point that boots the Compose window and initializes Chronos.
 */
fun main() = application {
    val registry = createSampleRegistry()
    val chronos = Chronos(registry)

    Window(onCloseRequest = ::exitApplication, title = "Chronos Sample") {
        App(chronos)
    }
}
