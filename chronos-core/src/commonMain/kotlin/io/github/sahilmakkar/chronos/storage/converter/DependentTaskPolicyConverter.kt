package io.github.sahilmakkar.chronos.storage.converter

import androidx.room3.ColumnTypeConverter
import io.github.sahilmakkar.chronos.task.policy.DependentTaskPolicy

/**
 * Room type converter for [DependentTaskPolicy] enum values.
 */
internal class DependentTaskPolicyConverter {
    @ColumnTypeConverter
    fun fromDependentTaskPolicy(dependentTaskPolicy: DependentTaskPolicy): String {
        return dependentTaskPolicy.name
    }

    @ColumnTypeConverter
    fun toDependentTaskPolicy(dependentTaskPolicy: String): DependentTaskPolicy {
        return DependentTaskPolicy.valueOf(dependentTaskPolicy)
    }
}
