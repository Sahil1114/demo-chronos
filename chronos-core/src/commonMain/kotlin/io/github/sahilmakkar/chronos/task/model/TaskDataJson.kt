package io.github.sahilmakkar.chronos.task.model

import kotlinx.serialization.json.*

/**
 * Serializes [TaskData] key-value pairs into a standardized JSON string representation.
 *
 * Supported values: [String], [Number], [Boolean]. Complex unsupported types serialize to null.
 */
internal fun TaskData.toJson(): String {
    val jsonMap = values.mapValues { (_, value) ->
        when (value) {
            is String -> JsonPrimitive(value = value)
            is Number -> JsonPrimitive(value = value)
            is Boolean -> JsonPrimitive(value = value)
            else -> JsonNull
        }
    }
    return Json.encodeToString(serializer = JsonObject.serializer(), value = JsonObject(content = jsonMap))
}

/**
 * Deserializes a JSON string back into a [TaskData] instance.
 *
 * Silently ignores malformed or unknown element structures to prevent deserialization crashes.
 */
internal fun TaskData.Companion.fromJson(json: String): TaskData {
    if (json.isBlank()) return TaskData()

    return try {
        val jsonObject = Json.decodeFromString(deserializer = JsonObject.serializer(), string = json)
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

        TaskData(values = map)
    } catch (e: Exception) {
        TaskData()
    }
}