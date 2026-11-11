package io.github.sahilmakkar.chronos.port

import io.github.sahilmakkar.chronos.storage.TaskEntity

/**
 * Internal architecture port evaluating whether hardware and environmental constraints
 * required by a [TaskEntity] are satisfied by the current host device state.
 *
 * Implementations are platform-specific (Android, iOS, JVM) and query system services such as
 * network managers, battery monitors, and storage metrics to ensure tasks only run when all
 * specified execution prerequisites are met.
 */
internal interface ConstraintVerifier {

    /**
     * Checks whether all constraints defined on [entity] are currently satisfied.
     *
     * Evaluates constraints including:
     * - Required network type ([io.github.sahilmakkar.chronos.task.constraints.NetworkType.CONNECTED] or
     *   [io.github.sahilmakkar.chronos.task.constraints.NetworkType.UNMETERED]).
     * - Battery charging requirement ([TaskEntity.requiresCharging]).
     * - Battery not low requirement ([TaskEntity.requiresBatteryNotLow]).
     * - Device storage not low requirement ([TaskEntity.requiresStorageNotLow]).
     * - Device idle state requirement ([TaskEntity.requiresDeviceIdle]).
     * - Allowed time window of day ([TaskEntity.windowStartHour], [TaskEntity.windowEndHour], [TaskEntity.windowTimeZone]).
     *
     * @param entity The persistent task representation containing constraint requirements.
     * @return `true` if all constraints are satisfied or if no constraints were specified;
     *   `false` if at least one constraint is currently violated.
     */
    fun isSatisfied(entity: TaskEntity): Boolean
}
