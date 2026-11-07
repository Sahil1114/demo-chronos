package io.github.sahilmakkar.chronos.constraints

import io.github.sahilmakkar.chronos.port.NetworkChecker
import io.github.sahilmakkar.chronos.storage.TaskEntity
import java.io.File

import kotlin.time.Clock

/**
 * JVM implementation of [ConstraintVerifier] evaluating network and execution window constraints.
 */
internal class JvmConstraintVerifier(
    networkChecker: NetworkChecker,
    clock: Clock = Clock.System
) : BaseConstraintVerifier(networkChecker, clock) {

    override fun isSatisfied(entity: TaskEntity): Boolean {
        if (!verifyNetwork(entity)) return false
        if (entity.requiresStorageNotLow && !isStorageNotLow()) return false
        if (!verifyTimeWindow(entity)) return false
        return true
    }

    private fun isStorageNotLow(): Boolean =
        File(System.getProperty("user.home")).usableSpace > 100_000_000L
}
