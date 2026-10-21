package io.github.sahilmakkar.chronos.storage.converter

import androidx.room3.ColumnTypeConverter
import io.github.sahilmakkar.chronos.task.policy.DependentTaskPolicy

class DependentTaskPolicyConverter {
    @ColumnTypeConverter
    fun fromDependentTaskPolicy(dependentTaskPolicy: DependentTaskPolicy): String {
        return dependentTaskPolicy.name
    }

    @ColumnTypeConverter
    fun toDependentTaskPolicy(dependentTaskPolicy: String): DependentTaskPolicy {
        return DependentTaskPolicy.valueOf(dependentTaskPolicy)
    }
}
