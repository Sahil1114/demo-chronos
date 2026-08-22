package io.github.sahilmakkar.chronos.data.mapper

import io.github.sahilmakkar.chronos.task.model.TaskData
import kotlinx.serialization.json.*

/**
 * Serializes this [TaskData] into a JSON string for persistence.
 */
fun TaskData.toJson(): String {
    val jsonMap = values.mapValues { (_, value) ->
        when (value) {
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            else -> JsonNull
        }
    }
    return Json.encodeToString(JsonObject.serializer(), JsonObject(jsonMap))
}

/**
 * Deserializes a [TaskData] instance from a JSON string previously produced by [toJson].
 *
 * Note: numeric values widen to [Long] before falling back to [Double] — see
 * [TaskData]'s class documentation for why exact Int/Long identity isn't preserved.
 *
 * @param json The JSON string representation of key-values.
 * @return A deserialized [TaskData] instance. Returns an empty [TaskData] if [json]
 *   is blank or malformed.
 */
fun TaskData.Companion.fromJson(json: String): TaskData {
    if (json.isBlank()) return TaskData()

    return try {
        val jsonObject = Json.decodeFromString(JsonObject.serializer(), json)
        val map = jsonObject.mapValues { (_, jsonElement) ->
            if (jsonElement is JsonPrimitive) {
                if (jsonElement.isString) {
                    jsonElement.content
                } else {
                    jsonElement.booleanOrNull
                        ?: jsonElement.longOrNull
                        ?: jsonElement.doubleOrNull
                        ?: jsonElement.content
                }
            } else {
                null
            }
        }.filterValues { it != null } as Map<String, Any>

        TaskData(map)
    } catch (e: Exception) {
        TaskData()
    }
}