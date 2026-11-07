package io.github.sahilmakkar.chronos.task.model

/**
 * Key-value payload container for task inputs, outputs, and progress.
 */
public data class TaskData(public val values: Map<String, Any> = emptyMap()) {

    public fun getString(key: String): String? = values[key] as? String
    public fun getInt(key: String, defaultValue: Int): Int =
        (values[key] as? Number)?.toInt() ?: defaultValue

    public fun getLong(key: String, defaultValue: Long): Long =
        (values[key] as? Number)?.toLong() ?: defaultValue

    public fun getDouble(key: String, defaultValue: Double): Double =
        (values[key] as? Number)?.toDouble() ?: defaultValue

    public fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        values[key] as? Boolean ?: defaultValue

    public fun toMap(): Map<String, Any> = values

    public fun isEmpty(): Boolean = values.isEmpty()

    public fun isNotEmpty(): Boolean = values.isNotEmpty()

    public val size: Int get() = values.size

    public companion object
}

/**
 * Constructs a [TaskData] instance from key-value pairs.
 */
public fun taskDataOf(vararg pairs: Pair<String, Any>): TaskData {
    return TaskData(pairs.toMap())
}
