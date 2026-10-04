package io.github.sahilmakkar.chronos.sample

import androidx.compose.ui.window.ComposeUIViewController
import io.github.sahilmakkar.chronos.Chronos
import io.github.sahilmakkar.chronos.PlatformSupport
import io.github.sahilmakkar.chronos.initialize
import platform.UIKit.UIViewController

private val registry by lazy { createSampleRegistry() }
private val chronos: Chronos by lazy {
    Chronos.initialize(support = PlatformSupport(), registry = registry)
}

fun MainViewController(): UIViewController = ComposeUIViewController {
    App(chronos = chronos)
}