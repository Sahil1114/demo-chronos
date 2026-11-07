package io.github.sahilmakkar.chronos.constraints

import io.github.sahilmakkar.chronos.port.ConstraintVerifier
import io.github.sahilmakkar.chronos.port.NetworkChecker
import io.github.sahilmakkar.chronos.storage.TaskEntity
import io.github.sahilmakkar.chronos.task.constraints.NetworkType
import io.github.sahilmakkar.chronos.task.constraints.TimeWindow
import kotlin.time.Clock

internal abstract class BaseConstraintVerifier(
    private val networkChecker: NetworkChecker? = null,
    private val clock: Clock = Clock.System
) : ConstraintVerifier {

    protected fun verifyNetwork(entity: TaskEntity): Boolean {
        if (entity.networkType == NetworkType.NOT_REQUIRED) return true
        val checker = networkChecker ?: return true
        return when (entity.networkType) {
            NetworkType.NOT_REQUIRED -> true
            NetworkType.CONNECTED -> checker.isConnected()
            NetworkType.UNMETERED -> checker.isConnected() && checker.isUnmetered()
        }
    }

    protected fun verifyTimeWindow(entity: TaskEntity): Boolean {
        val start = entity.windowStartHour ?: return true
        val end = entity.windowEndHour ?: return true
        val timeZone = entity.windowTimeZone ?: return true
        return runCatching {
            TimeWindow(startHour = start, endHour = end, timeZone = timeZone)
                .contains(now = clock.now())
        }.getOrDefault(defaultValue = false)
    }
}
