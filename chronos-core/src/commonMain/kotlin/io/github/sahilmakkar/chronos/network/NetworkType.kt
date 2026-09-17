package io.github.sahilmakkar.chronos.network

import io.github.sahilmakkar.chronos.task.constraints.BestEffortOnIOS
import io.github.sahilmakkar.chronos.task.constraints.BestEffortOnJVM

enum class NetworkType {

    NOT_REQUIRED,

    CONNECTED,

    UNMETERED

}