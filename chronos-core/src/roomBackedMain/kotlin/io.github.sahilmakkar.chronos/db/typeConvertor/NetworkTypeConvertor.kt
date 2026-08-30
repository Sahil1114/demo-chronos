package io.github.sahilmakkar.chronos.db.typeConvertor

import androidx.room3.TypeConverter
import io.github.sahilmakkar.chronos.network.NetworkType
import io.github.sahilmakkar.chronos.task.policy.BackoffPolicy

class NetworkTypeConvertor {
    @TypeConverter
    fun fromNetworkType(networkType: NetworkType): String {
        return  networkType.name
    }
    
    @TypeConverter
    fun toNetworkType(networkType: String): NetworkType {
        return NetworkType.valueOf(networkType)
    }
}