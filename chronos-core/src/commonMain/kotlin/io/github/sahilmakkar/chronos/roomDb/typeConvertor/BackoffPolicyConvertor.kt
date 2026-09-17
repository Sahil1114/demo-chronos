package io.github.sahilmakkar.chronos.roomDb.typeConvertor

import androidx.room3.TypeConverter
import io.github.sahilmakkar.chronos.task.policy.BackoffPolicy

class BackoffPolicyConvertor {
    @TypeConverter
    fun fromBackoffPolicy(backoffPolicy: BackoffPolicy): String {
        return  backoffPolicy.name
    }

    @TypeConverter
    fun toBackoffPolicy(backoffPolicy: String): BackoffPolicy {
        return BackoffPolicy.valueOf(backoffPolicy)
    }
}