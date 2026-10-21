package io.github.sahilmakkar.chronos.constraints

import io.github.sahilmakkar.chronos.port.NetworkChecker
import io.github.sahilmakkar.chronos.storage.TaskEntity
import java.io.File

internal class JvmConstraintVerifier(
    networkChecker: NetworkChecker,
) : BaseConstraintVerifier(networkChecker) {

    override fun isSatisfied(entity: TaskEntity): Boolean {
        if (!verifyNetwork(entity)) return false
        if (entity.requiresStorageNotLow && !isStorageNotLow()) return false
        if (!verifyTimeWindow(entity)) return false
        return true
    }

    private fun isStorageNotLow(): Boolean =
        File(System.getProperty("user.home")).usableSpace > 100_000_000L
}

@Deprecated("Use JvmConstraintVerifier instead", ReplaceWith("JvmConstraintVerifier"))
internal typealias WorkConstraintVerifier = JvmConstraintVerifier
