package io.github.sahilmakkar.chronos.storage.converter

import androidx.room3.ColumnTypeConverter
import io.github.sahilmakkar.chronos.task.policy.BackoffPolicy

/**
 * Room type converter for [BackoffPolicy] enum values.
 *
 * Persists retry delay backoff strategies (`LINEAR` or `EXPONENTIAL`) as SQLite `TEXT` strings.
 */
internal class BackoffPolicyConverter {

    /**
     * Converts a [BackoffPolicy] enum entry to its SQLite text representation.
     *
     * @param backoffPolicy The backoff calculation strategy.
     * @return The canonical [Enum.name] string representation.
     */
    @ColumnTypeConverter
    fun fromBackoffPolicy(backoffPolicy: BackoffPolicy): String {
        return backoffPolicy.name
    }

    /**
     * Parses a stored SQLite text column into a [BackoffPolicy] constant.
     *
     * @param backoffPolicy The string stored in SQLite.
     * @return The reconstituted [BackoffPolicy] constant.
     * @throws IllegalArgumentException if the stored string is unrecognized.
     */
    @ColumnTypeConverter
    fun toBackoffPolicy(backoffPolicy: String): BackoffPolicy {
        return BackoffPolicy.valueOf(backoffPolicy)
    }
}
