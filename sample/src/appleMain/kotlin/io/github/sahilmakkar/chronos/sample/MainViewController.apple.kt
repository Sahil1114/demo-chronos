package io.github.sahilmakkar.chronos.sample

import androidx.compose.ui.window.ComposeUIViewController
import io.github.sahilmakkar.chronos.Chronos
import io.github.sahilmakkar.chronos.PlatformSupport
import io.github.sahilmakkar.chronos.initialize
import io.github.sahilmakkar.chronos.instance
import platform.UIKit.UIViewController

fun initChronos() {
    Chronos.initialize(support = PlatformSupport(), registry = createSampleRegistry())
}

fun MainViewController(): UIViewController = ComposeUIViewController {
    App(chronos = Chronos.instance)
}
