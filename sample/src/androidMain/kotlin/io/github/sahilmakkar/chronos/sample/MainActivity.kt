package io.github.sahilmakkar.chronos.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.sahilmakkar.chronos.Chronos
import io.github.sahilmakkar.chronos.instance

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            App(Chronos.instance)
        }
    }
}
