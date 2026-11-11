package io.github.sahilmakkar.chronos.storage.converter

import androidx.room3.ColumnTypeConverter
import io.github.sahilmakkar.chronos.task.constraints.NetworkType

/**
 * Room type converter for [NetworkType] enum values.
 *
 * Persists the enum name as a plain SQLite `TEXT` string column and converts it back
 * into the corresponding strongly typed enum entry upon query retrieval.
 */
internal class NetworkTypeConverter {

    /**
     * Converts a [NetworkType] enum entry to its string representation for SQLite storage.
     *
     * @param networkType The network constraint enum value.
     * @return The canonical [Enum.name] string.
     */
    @ColumnTypeConverter
    fun fromNetworkType(networkType: NetworkType): String {
        return networkType.name
    }

    /**
     * Parses a string retrieved from SQLite into its corresponding [NetworkType] enum value.
     *
     * @param networkType The string name stored in the database.
     * @return The matching [NetworkType] constant.
     * @throws IllegalArgumentException if the stored string does not match any enum constant.
     */
    @ColumnTypeConverter
    fun toNetworkType(networkType: String): NetworkType {
        return NetworkType.valueOf(value = networkType)
    }
}
