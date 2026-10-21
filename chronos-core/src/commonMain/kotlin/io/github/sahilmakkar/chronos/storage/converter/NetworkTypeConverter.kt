package io.github.sahilmakkar.chronos.storage.converter

import androidx.room3.ColumnTypeConverter
import io.github.sahilmakkar.chronos.task.constraints.NetworkType

class NetworkTypeConverter {
    @ColumnTypeConverter
    fun fromNetworkType(networkType: NetworkType): String {
        return networkType.name
    }

    @ColumnTypeConverter
    fun toNetworkType(networkType: String): NetworkType {
        return NetworkType.valueOf(networkType)
    }
}
