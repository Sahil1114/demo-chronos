package io.github.sahilmakkar.chronos.task.model

/**
 * A persistent key-value store used to pass parameters to [io.github.sahilmakkar.chronos.kmpWorker.KmpWorker]s and
 * return results.
 *
 * Supported value types are: [String], [Int], [Long], [Double], [Boolean].
 * Any other type is unsupported and will be dropped on serialization.
 *
 * Note: numeric type identity (e.g. [Int] vs [Long]) is not guaranteed to survive
 * persistence — JSON has no distinction between integer widths. Always read values
 * back using the typed getter matching the value's general category
 * ([getInt]/[getLong]/[getDouble]), never via a direct cast on [values].
 *
 * @property values The map containing the key-value pairs.
 */
data class TaskData(val values: Map<String, Any> = emptyMap()) {

    /**
     * Retrieves a [String] value associated with the given key, or null.
     */
    fun getString(key: String): String? = values[key] as? String

    /**
     * Retrieves an [Int] value associated with the given key, or returns [defaultValue].
     */
    fun getInt(key: String, defaultValue: Int): Int =
        (values[key] as? Number)?.toInt() ?: defaultValue

    /**
     * Retrieves a [Long] value associated with the given key, or returns [defaultValue].
     */
    fun getLong(key: String, defaultValue: Long): Long =
        (values[key] as? Number)?.toLong() ?: defaultValue

    /**
     * Retrieves a [Double] value associated with the given key, or returns [defaultValue].
     */
    fun getDouble(key: String, defaultValue: Double): Double =
        (values[key] as? Number)?.toDouble() ?: defaultValue

    /**
     * Retrieves a [Boolean] value associated with the given key, or returns [defaultValue].
     */
    fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        values[key] as? Boolean ?: defaultValue

    /**
     * Returns a standard [Map] representation of the underlying data.
     */
    fun toMap(): Map<String, Any> = values

    companion object {}

}

/**
 * Utility builder method to create a [TaskData] instance from vararg pairs.
 */
fun workDataOf(vararg pairs: Pair<String, Any>): TaskData {
    return TaskData(pairs.toMap())
}