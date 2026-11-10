package io.github.sahilmakkar.chronos.constraints

import io.github.sahilmakkar.chronos.port.ConstraintVerifier
import io.github.sahilmakkar.chronos.port.NetworkChecker
import io.github.sahilmakkar.chronos.storage.TaskEntity
import io.github.sahilmakkar.chronos.task.constraints.NetworkType
import io.github.sahilmakkar.chronos.task.constraints.TimeWindow
import kotlin.time.Clock

/**
 * Common base class providing platform-agnostic constraint checking logic.
 *
 * Implements shared network connectivity verification against [NetworkChecker] and
 * time-of-day execution window verification using [TimeWindow]. Platform-specific subclasses
 * extend this class to add hardware monitoring (battery level, charging status, available storage).
 *
 * @param networkChecker Optional platform network checker for inspecting active connectivity.
 * @param clock Clock abstraction used for time window comparisons. Defaults to [Clock.System].
 */
internal abstract class BaseConstraintVerifier(
    private val networkChecker: NetworkChecker? = null,
    private val clock: Clock = Clock.System
) : ConstraintVerifier {

    /**
     * Evaluates whether the device currently meets the network requirements specified in [entity].
     *
     * - If [TaskEntity.networkType] is [NetworkType.NOT_REQUIRED], returns `true`.
     * - If no [NetworkChecker] is available, defaults to `true`.
     * - If [NetworkType.CONNECTED], verifies active network connection.
     * - If [NetworkType.UNMETERED], verifies active and unmetered (e.g. Wi-Fi) connection.
     *
     * @param entity The persistent task representation containing network constraints.
     * @return `true` if current network conditions satisfy requirements; `false` otherwise.
     */
    protected fun verifyNetwork(entity: TaskEntity): Boolean {
        if (entity.networkType == NetworkType.NOT_REQUIRED) return true
        val checker = networkChecker ?: return true
        return when (entity.networkType) {
            NetworkType.NOT_REQUIRED -> true
            NetworkType.CONNECTED -> checker.isConnected()
            NetworkType.UNMETERED -> checker.isConnected() && checker.isUnmetered()
        }
    }

    /**
     * Evaluates whether the current clock time falls within the allowed daily execution window.
     *
     * If any window component ([TaskEntity.windowStartHour], [TaskEntity.windowEndHour],
     * [TaskEntity.windowTimeZone]) is omitted, the task has no time restrictions and returns `true`.
     * Handles both same-day windows (e.g. 09:00 to 17:00) and overnight wrapping windows
     * (e.g. 22:00 to 06:00).
     *
     * @param entity The persistent task representation containing optional time window parameters.
     * @return `true` if the current time falls within the window or if no window is defined;
     *   `false` if outside the allowed hours or if time calculation fails.
     */
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
