package io.github.sahilmakkar.chronos.port

import io.github.sahilmakkar.chronos.storage.TaskEntity

/**
 * Evaluates whether environmental constraints for a task entity are currently satisfied.
 */
internal interface ConstraintVerifier {
    fun isSatisfied(entity: TaskEntity): Boolean
}
