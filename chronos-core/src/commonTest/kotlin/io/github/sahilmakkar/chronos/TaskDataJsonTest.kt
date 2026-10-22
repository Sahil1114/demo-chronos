package io.github.sahilmakkar.chronos

import io.github.sahilmakkar.chronos.task.model.TaskData
import io.github.sahilmakkar.chronos.task.model.fromJson
import io.github.sahilmakkar.chronos.task.model.taskDataOf
import io.github.sahilmakkar.chronos.task.model.toJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskDataJsonTest {

    @Test
    fun testBasicSerializationAndDeserialization() {
        val original = taskDataOf(
            "str" to "value",
            "int" to 123,
            "long" to 9876543210L,
            "double" to 2.718,
            "bool" to true
        )
        val json = original.toJson()
        val restored = TaskData.fromJson(json)

        assertEquals("value", restored.getString("str"))
        assertEquals(123, restored.getInt("int", 0))
        assertEquals(9876543210L, restored.getLong("long", 0L))
        assertEquals(2.718, restored.getDouble("double", 0.0))
        assertEquals(true, restored.getBoolean("bool", false))
    }

    @Test
    fun testSpecialCharacters() {
        val original = taskDataOf(
            "quote" to "Hello \"World\"",
            "multiline" to "Line 1\nLine 2\r\nLine 3",
            "slash" to "C:\\path\\to\\dir"
        )
        val json = original.toJson()
        val restored = TaskData.fromJson(json)

        assertEquals("Hello \"World\"", restored.getString("quote"))
        assertEquals("Line 1\nLine 2\r\nLine 3", restored.getString("multiline"))
        assertEquals("C:\\path\\to\\dir", restored.getString("slash"))
    }

    @Test
    fun testNestedAndNullValuesIgnoredGracefully() {
        val jsonWithNested = """
            {
                "validKey": "validValue",
                "intKey": 42,
                "nullKey": null,
                "nestedObj": { "inner": "data" },
                "nestedArr": [1, 2, 3]
            }
        """.trimIndent()

        val restored = TaskData.fromJson(jsonWithNested)
        assertEquals("validValue", restored.getString("validKey"))
        assertEquals(42, restored.getInt("intKey", 0))
        assertEquals(null, restored.getString("nullKey"))
        assertEquals(null, restored.getString("nestedObj"))
        assertEquals(null, restored.getString("nestedArr"))
    }

    @Test
    fun testEmptyAndMalformedJson() {
        assertTrue(TaskData.fromJson("").isEmpty())
        assertTrue(TaskData.fromJson("   ").isEmpty())
        assertTrue(TaskData.fromJson("not-a-json").isEmpty())
        assertTrue(TaskData.fromJson("{invalid json").isEmpty())
    }
}
