package io.github.sahilmakkar.chronos.task.model

data class TaskData(val values: Map<String, Any> = emptyMap()) {

    fun getString(key: String): String? = values[key] as? String
    fun getInt(key: String, defaultValue: Int): Int =
        (values[key] as? Number)?.toInt() ?: defaultValue

    fun getLong(key: String, defaultValue: Long): Long =
        (values[key] as? Number)?.toLong() ?: defaultValue

    fun getDouble(key: String, defaultValue: Double): Double =
        (values[key] as? Number)?.toDouble() ?: defaultValue

    fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        values[key] as? Boolean ?: defaultValue

    fun toMap(): Map<String, Any> = values

    companion object {}

}

fun workDataOf(vararg pairs: Pair<String, Any>): TaskData {
    return TaskData(pairs.toMap())
}