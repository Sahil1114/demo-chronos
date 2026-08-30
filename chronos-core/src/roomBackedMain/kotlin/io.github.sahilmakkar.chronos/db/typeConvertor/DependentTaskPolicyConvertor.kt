package io.github.sahilmakkar.chronos.db.typeConvertor

import androidx.room3.TypeConverter
import io.github.sahilmakkar.chronos.task.policy.BackoffPolicy
import io.github.sahilmakkar.chronos.task.policy.DependentTaskPolicy

class DependentTaskPolicyConvertor {
    @TypeConverter
    fun fromDependentTaskPolicy(dependentTaskPolicy: DependentTaskPolicy): String {
        return  dependentTaskPolicy.name
    }

    @TypeConverter
    fun toDependentTaskPolicy(dependentTaskPolicy: String): DependentTaskPolicy {
        return DependentTaskPolicy.valueOf(dependentTaskPolicy)
    }
}