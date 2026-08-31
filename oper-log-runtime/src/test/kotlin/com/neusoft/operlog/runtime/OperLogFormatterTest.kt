package com.neusoft.operlog.runtime

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class OperLogFormatterTest {

    class FaultyToStringObject {
        override fun toString(): String {
            throw IllegalStateException("Intentional toString failure")
        }
    }

    @Test
    fun testSelfReferencingList() {
        val list = mutableListOf<Any>()
        list.add("item1")
        list.add(list)

        val result = OperLogFormatter.formatValue(list)
        assertTrue(result.contains("<circular-reference>"), "Self-referencing list should contain <circular-reference>, got: $result")
    }

    @Test
    fun testSelfReferencingMap() {
        val map = mutableMapOf<String, Any>()
        map["key1"] = "val1"
        map["self"] = map

        val result = OperLogFormatter.formatValue(map)
        assertTrue(result.contains("<circular-reference>"), "Self-referencing map should contain <circular-reference>, got: $result")
    }

    @Test
    fun testDeeplyNestedStructure() {
        var current: Any = "deepest"
        for (i in 0..10) {
            current = listOf(current)
        }

        val result = OperLogFormatter.formatValue(current)
        assertTrue(result.contains("<max-depth-reached>"), "Deeply nested list should trigger <max-depth-reached>, got: $result")
    }

    @Test
    fun testFaultyToStringObject() {
        val faulty = FaultyToStringObject()
        val result = OperLogFormatter.formatValue(faulty)
        assertTrue(result.contains("<toString() failed: IllegalStateException>"), "Faulty toString should be safely handled, got: $result")
    }

    @Test
    fun testNegativeConfigurationSafety() {
        val originalMaxVal = OperLogConfig.maxValueLength
        val originalMaxCol = OperLogConfig.maxCollectionSize

        try {
            OperLogConfig.maxValueLength = -10
            OperLogConfig.maxCollectionSize = -5

            val list = listOf("a", "b", "c")
            val result = OperLogFormatter.formatValue(list)
            assertTrue(result.isNotEmpty(), "Formatting with negative configs should not throw exception")
        } finally {
            OperLogConfig.maxValueLength = originalMaxVal
            OperLogConfig.maxCollectionSize = originalMaxCol
        }
    }

    @Test
    fun testFormatArgsWithMasking() {
        val names = arrayOf("user", "password", "token")
        val values = arrayOf<Any?>("admin", "secret123", "tok888")
        val ignored = intArrayOf(1, 2)

        val formatted = OperLogFormatter.formatArgs(names, values, ignored)
        assertTrue(formatted.contains("user=\"admin\""), "User arg should be formatted")
        assertTrue(formatted.contains("password=***"), "Password should be masked")
        assertTrue(formatted.contains("token=***"), "Token should be masked")
    }
}
