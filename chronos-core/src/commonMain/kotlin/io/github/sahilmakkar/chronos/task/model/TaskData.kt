package io.github.sahilmakkar.chronos.task.model

/**
 * An immutable key-value payload container used to pass parameters, progress updates, and results to and from [io.github.sahilmakkar.chronos.worker.TaskWorker] instances.
 *
 * `TaskData` is serialized to and from JSON when persisted in the Room SQLite database.
 * Supported primitive value types include:
 * - [String]
 * - [Int], [Long], [Double], [Float] (stored and accessed numerically)
 * - [Boolean]
 *
 * ### Example:
 * ```kotlin
 * val input = taskDataOf(
 *     "userId" to 42,
 *     "syncToken" to "abc-xyz",
 *     "retryAllowed" to true
 * )
 *
 * val userId = input.getInt("userId", defaultValue = -1)
 * val token = input.getString("syncToken")
 * ```
 *
 * @property values Underlying map of serializable key-value entries.
 *
 * @see taskDataOf
 * @see io.github.sahilmakkar.chronos.task.model.TaskInfo
 */
public data class TaskData(public val values: Map<String, Any> = emptyMap()) {

    /**
     * Retrieves the String value associated with [key], or null if absent or of another type.
     */
    public fun getString(key: String): String? = values[key] as? String

    /**
     * Retrieves the Int value associated with [key], or [defaultValue] if absent or not a number.
     */
    public fun getInt(key: String, defaultValue: Int): Int =
        (values[key] as? Number)?.toInt() ?: defaultValue

    /**
     * Retrieves the Long value associated with [key], or [defaultValue] if absent or not a number.
     */
    public fun getLong(key: String, defaultValue: Long): Long =
        (values[key] as? Number)?.toLong() ?: defaultValue

    /**
     * Retrieves the Double value associated with [key], or [defaultValue] if absent or not a number.
     */
    public fun getDouble(key: String, defaultValue: Double): Double =
        (values[key] as? Number)?.toDouble() ?: defaultValue

    /**
     * Retrieves the Boolean value associated with [key], or [defaultValue] if absent or of another type.
     */
    public fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        values[key] as? Boolean ?: defaultValue

    /**
     * Returns an immutable copy of the key-value map.
     */
    public fun toMap(): Map<String, Any> = values

    /**
     * Returns true if this payload contains no entries.
     */
    public fun isEmpty(): Boolean = values.isEmpty()

    /**
     * Returns true if this payload contains at least one entry.
     */
    public fun isNotEmpty(): Boolean = values.isNotEmpty()

    /**
     * Returns the count of key-value pairs contained in this payload.
     */
    public val size: Int get() = values.size

    public companion object
}

/**
 * Constructs a [TaskData] instance from key-value pairs.
 */
public fun taskDataOf(vararg pairs: Pair<String, Any>): TaskData {
    return TaskData(pairs.toMap())
}
