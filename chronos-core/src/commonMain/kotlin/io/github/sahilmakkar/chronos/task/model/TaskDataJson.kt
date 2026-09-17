package io.github.sahilmakkar.chronos.task.model

import kotlinx.serialization.json.*

fun TaskData.toJson(): String {
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
        }.filterValues { it != null } as  Map<String, Any>

        TaskData(map)
    } catch (e: Exception) {
        TaskData()
    }
}