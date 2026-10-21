package io.github.sahilmakkar.chronos.port

import io.github.sahilmakkar.chronos.storage.TaskEntity

internal interface ConstraintVerifier {
    fun isSatisfied(entity: TaskEntity): Boolean
}
