package com.neusoft.operlog.runtime

import java.lang.reflect.Array as JavaArray

/**
 * Formats method parameters, return values, and objects safely with size and length limits.
 */
object OperLogFormatter {

    fun formatArgs(
        names: Array<String>?,
        values: Array<Any?>?,
        ignoredIndexes: IntArray?
    ): String {
        if (values == null || values.isEmpty()) {
            return ""
        }

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
                sb.append(formatValue(values[i]))
            }
        }

        return sb.toString()
    }

    fun formatValue(value: Any?): String {
        if (value == null) {
            return "null"
        }

        val formatted = when (value) {
            is String -> "\"${truncateString(value)}\""
            is Char -> "'$value'"
            is Boolean, is Byte, is Short, is Int, is Long, is Float, is Double, is Number -> value.toString()
            is Enum<*> -> value.name
            is Collection<*> -> formatCollection(value)
            is Map<*, *> -> formatMap(value)
            else -> {
                if (value.javaClass.isArray) {
                    formatArray(value)
                } else {
                    formatObject(value)
                }
            }
        }

        return truncateString(formatted)
    }

    private fun formatCollection(col: Collection<*>): String {
        val maxSize = OperLogConfig.maxCollectionSize
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
            sb.append(formatValue(item))
            count++
        }

        sb.append("]")
        return sb.toString()
    }

    private fun formatArray(array: Any): String {
        val length = JavaArray.getLength(array)
        val maxSize = OperLogConfig.maxArraySize
        val sb = StringBuilder("[")

        val limit = Math.min(length, maxSize)
        for (i in 0 until limit) {
            if (i > 0) {
                sb.append(", ")
            }
            val element = JavaArray.get(array, i)
            sb.append(formatValue(element))
        }

        if (length > maxSize) {
            sb.append(", ...[truncated ${length - maxSize} items]")
        }

        sb.append("]")
        return sb.toString()
    }

    private fun formatMap(map: Map<*, *>): String {
        val maxSize = OperLogConfig.maxMapSize
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
            sb.append(formatValue(key)).append("=").append(formatValue(value))
            count++
        }

        sb.append("}")
        return sb.toString()
    }

    private fun formatObject(obj: Any): String {
        return try {
            obj.toString()
        } catch (t: Throwable) {
            "${obj.javaClass.name}@${Integer.toHexString(System.identityHashCode(obj))}"
        }
    }

    private fun truncateString(str: String): String {
        val maxLen = OperLogConfig.maxValueLength
        if (str.length <= maxLen) {
            return str
        }
        return str.substring(0, maxLen) + "...[truncated]"
    }
}
