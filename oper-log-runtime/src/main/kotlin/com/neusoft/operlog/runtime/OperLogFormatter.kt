package com.neusoft.operlog.runtime

import java.util.Collections
import java.util.IdentityHashMap
import java.lang.reflect.Array as JavaArray

/**
 * Formats method parameters, return values, and objects safely with size, length, and recursion limits.
 */
object OperLogFormatter {

    private const val MAX_RECURSION_DEPTH = 5

    fun formatArgs(
        names: Array<String>?,
        values: Array<Any?>?,
        ignoredIndexes: IntArray?
    ): String {
        if (values == null || values.isEmpty()) {
            return ""
        }

        return try {
            val ignoredSet = ignoredIndexes?.toSet() ?: emptySet()
            val sb = StringBuilder()

            for (i in values.indices) {
                if (i > 0) {
                    sb.append(", ")
                }

                val paramName = if (names != null && i < names.size && names[i].isNotEmpty()) {
                    names[i]
                } else {
                    "arg$i"
                }

                sb.append(paramName).append("=")

                if (ignoredSet.contains(i)) {
                    sb.append(SensitiveValuePolicy.mask(values[i]))
                } else {
                    val visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
                    sb.append(formatValueInternal(values[i], visited, 0))
                }
            }

            sb.toString()
        } catch (t: Throwable) {
            if (t is VirtualMachineError || t is ThreadDeath) throw t
            "<args formatting failed>"
        }
    }

    fun formatValue(value: Any?): String {
        return try {
            val visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
            formatValueInternal(value, visited, 0)
        } catch (t: Throwable) {
            if (t is VirtualMachineError || t is ThreadDeath) throw t
            "<value formatting failed>"
        }
    }

    private fun formatValueInternal(
        value: Any?,
        visited: MutableSet<Any>,
        depth: Int
    ): String {
        if (value == null) {
            return "null"
        }

        if (depth > MAX_RECURSION_DEPTH) {
            return "<max-depth-reached>"
        }

        val formatted = when (value) {
            is String -> "\"${truncateString(value)}\""
            is Char -> "'$value'"
            is Boolean, is Byte, is Short, is Int, is Long, is Float, is Double, is Number -> value.toString()
            is Enum<*> -> value.name
            is Collection<*> -> formatCollection(value, visited, depth)
            is Map<*, *> -> formatMap(value, visited, depth)
            else -> {
                if (value.javaClass.isArray) {
                    formatArray(value, visited, depth)
                } else {
                    formatObject(value)
                }
            }
        }

        return truncateString(formatted)
    }

    private fun formatCollection(
        col: Collection<*>,
        visited: MutableSet<Any>,
        depth: Int
    ): String {
        if (visited.contains(col)) {
            return "<circular-reference>"
        }
        visited.add(col)

        try {
            val maxSize = maxOf(0, OperLogConfig.maxCollectionSize)
            val sb = StringBuilder("[")
            var count = 0

            for (item in col) {
                if (count >= maxSize) {
                    sb.append(", ...[truncated ${col.size - count} items]")
                    break
                }
                if (count > 0) {
                    sb.append(", ")
                }
                sb.append(formatValueInternal(item, visited, depth + 1))
                count++
            }

            sb.append("]")
            return sb.toString()
        } finally {
            visited.remove(col)
        }
    }

    private fun formatArray(
        array: Any,
        visited: MutableSet<Any>,
        depth: Int
    ): String {
        if (visited.contains(array)) {
            return "<circular-reference>"
        }
        visited.add(array)

        try {
            val length = JavaArray.getLength(array)
            val maxSize = maxOf(0, OperLogConfig.maxArraySize)
            val sb = StringBuilder("[")

            val limit = Math.min(length, maxSize)
            for (i in 0 until limit) {
                if (i > 0) {
                    sb.append(", ")
                }
                val element = JavaArray.get(array, i)
                sb.append(formatValueInternal(element, visited, depth + 1))
            }

            if (length > maxSize) {
                sb.append(", ...[truncated ${length - maxSize} items]")
            }

            sb.append("]")
            return sb.toString()
        } finally {
            visited.remove(array)
        }
    }

    private fun formatMap(
        map: Map<*, *>,
        visited: MutableSet<Any>,
        depth: Int
    ): String {
        if (visited.contains(map)) {
            return "<circular-reference>"
        }
        visited.add(map)

        try {
            val maxSize = maxOf(0, OperLogConfig.maxMapSize)
            val sb = StringBuilder("{")
            var count = 0

            for ((key, value) in map) {
                if (count >= maxSize) {
                    sb.append(", ...[truncated ${map.size - count} entries]")
                    break
                }
                if (count > 0) {
                    sb.append(", ")
                }
                sb.append(formatValueInternal(key, visited, depth + 1))
                    .append("=")
                    .append(formatValueInternal(value, visited, depth + 1))
                count++
            }

            sb.append("}")
            return sb.toString()
        } finally {
            visited.remove(map)
        }
    }

    private fun formatObject(obj: Any): String {
        return try {
            obj.toString()
        } catch (t: Throwable) {
            if (t is VirtualMachineError || t is ThreadDeath) throw t
            "<toString() failed: ${t.javaClass.simpleName}>"
        }
    }

    private fun truncateString(str: String): String {
        val maxLen = maxOf(0, OperLogConfig.maxValueLength)
        if (str.length <= maxLen) {
            return str
        }
        return str.substring(0, maxLen) + "...[truncated]"
    }
}
