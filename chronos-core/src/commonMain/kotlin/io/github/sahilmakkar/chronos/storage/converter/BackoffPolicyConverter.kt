package io.github.sahilmakkar.chronos.storage.converter

import androidx.room3.ColumnTypeConverter
import io.github.sahilmakkar.chronos.task.policy.BackoffPolicy

/**
 * Room type converter for [BackoffPolicy] enum values.
 */
internal class BackoffPolicyConverter {
    @ColumnTypeConverter
    fun fromBackoffPolicy(backoffPolicy: BackoffPolicy): String {
        return backoffPolicy.name
    }

    @ColumnTypeConverter
    fun toBackoffPolicy(backoffPolicy: String): BackoffPolicy {
        return BackoffPolicy.valueOf(backoffPolicy)
    }
}
