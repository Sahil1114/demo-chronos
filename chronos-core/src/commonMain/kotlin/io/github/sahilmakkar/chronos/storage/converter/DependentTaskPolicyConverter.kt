package io.github.sahilmakkar.chronos.storage.converter

import androidx.room3.ColumnTypeConverter
import io.github.sahilmakkar.chronos.task.policy.DependentTaskPolicy

/**
 * Room type converter for [DependentTaskPolicy] enum values.
 *
 * Persists dependency edge failure propagation policies (`STOP_ON_FAILURE` or `CONTINUE_ON_FAILURE`)
 * as SQLite `TEXT` strings.
 */
internal class DependentTaskPolicyConverter {

    /**
     * Converts a [DependentTaskPolicy] enum entry to its SQLite text representation.
     *
     * @param dependentTaskPolicy The dependent task policy value.
     * @return The canonical [Enum.name] string representation.
     */
    @ColumnTypeConverter
    fun fromDependentTaskPolicy(dependentTaskPolicy: DependentTaskPolicy): String {
        return dependentTaskPolicy.name
    }

    /**
     * Parses a stored SQLite text column into a [DependentTaskPolicy] constant.
     *
     * @param dependentTaskPolicy The string stored in SQLite.
     * @return The reconstituted [DependentTaskPolicy] constant.
     * @throws IllegalArgumentException if the stored string is unrecognized.
     */
    @ColumnTypeConverter
    fun toDependentTaskPolicy(dependentTaskPolicy: String): DependentTaskPolicy {
        return DependentTaskPolicy.valueOf(dependentTaskPolicy)
    }
}
