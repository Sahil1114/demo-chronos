package io.github.sahilmakkar.chronos.constraints

import io.github.sahilmakkar.chronos.port.NetworkChecker
import io.github.sahilmakkar.chronos.storage.TaskEntity
import java.io.File

import kotlin.time.Clock

/**
 * JVM implementation of [ConstraintVerifier] evaluating network and execution window constraints.
 *
 * Checks standard host filesystem usable space (> 100MB in the user's home directory),
 * network reachability via Java NIO/networking APIs, and time window restrictions.
 *
 * @param networkChecker Network connectivity verifier.
 * @param clock Time provider for evaluating time windows. Defaults to [Clock.System].
 */
internal class JvmConstraintVerifier(
    networkChecker: NetworkChecker,
    clock: Clock = Clock.System
) : BaseConstraintVerifier(networkChecker, clock) {

    /**
     * Checks if all constraints defined on [entity] are met on JVM.
     *
     * @param entity The task record containing constraints to check.
     * @return `true` if all constraints pass; `false` otherwise.
     */
    override fun isSatisfied(entity: TaskEntity): Boolean {
        if (!verifyNetwork(entity)) return false
        if (entity.requiresStorageNotLow && !isStorageNotLow()) return false
        if (!verifyTimeWindow(entity)) return false
        return true
    }

    /**
     * Verifies whether usable disk space on the user home volume exceeds 100MB.
     *
     * @return `true` if free usable space is greater than 100MB; `false` otherwise.
     */
    private fun isStorageNotLow(): Boolean =
        File(System.getProperty("user.home")).usableSpace > 100_000_000L
}
