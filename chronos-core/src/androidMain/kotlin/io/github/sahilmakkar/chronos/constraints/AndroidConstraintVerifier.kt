package io.github.sahilmakkar.chronos.constraints

import io.github.sahilmakkar.chronos.storage.TaskEntity

internal class AndroidConstraintVerifier : BaseConstraintVerifier() {
    override fun isSatisfied(entity: TaskEntity): Boolean = verifyTimeWindow(entity)
}

@Deprecated("Use AndroidConstraintVerifier instead", ReplaceWith("AndroidConstraintVerifier"))
internal typealias WorkConstraintVerifier = AndroidConstraintVerifier
