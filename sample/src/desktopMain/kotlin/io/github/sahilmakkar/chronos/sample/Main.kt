package io.github.sahilmakkar.chronos.sample

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.sahilmakkar.chronos.Chronos
import io.github.sahilmakkar.chronos.PlatformSupport
import io.github.sahilmakkar.chronos.initialize

fun main() = application {
    val registry = remember { createSampleRegistry() }
    val chronos = remember { Chronos.initialize(support = PlatformSupport(), registry = registry) }

    DisposableEffect(Unit) {
        onDispose { chronos.close() }
    }

    Window(onCloseRequest = ::exitApplication, title = "Chronos KMP Scheduler") {
        App(chronos = chronos)
    }
}