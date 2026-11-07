package io.github.sahilmakkar.chronos.task.model

import kotlinx.serialization.json.*

/**
 * Serializes [TaskData] key-value pairs into a JSON string.
 */
internal fun TaskData.toJson(): String {
    val jsonMap = values.mapValues { (_, value) ->
        when (value) {
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            else -> JsonNull
        }
    }
    return Json.encodeToString(serializer = JsonObject.serializer(), value = JsonObject(jsonMap))
}

internal fun TaskData.Companion.fromJson(json: String): TaskData {
    if (json.isBlank()) return TaskData()

    return try {
        val jsonObject = Json.decodeFromString(JsonObject.serializer(), json)
        val map = buildMap {
            jsonObject.forEach { (key, jsonElement) ->
                if (jsonElement is JsonPrimitive && jsonElement !is JsonNull) {
                    val value: Any = if (jsonElement.isString) {
                        jsonElement.content
                    } else {
                        jsonElement.booleanOrNull
                            ?: jsonElement.longOrNull
                            ?: jsonElement.doubleOrNull
                            ?: jsonElement.content
                    }
                    put(key, value)
                }
            }
        }

        TaskData(map)
    } catch (e: Exception) {
        TaskData()
    }
}