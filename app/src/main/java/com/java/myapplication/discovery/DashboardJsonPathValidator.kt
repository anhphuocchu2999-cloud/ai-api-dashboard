package com.java.myapplication.discovery

import org.json.JSONArray
import org.json.JSONObject

data class JsonPathResolution(
    val pathSafe: Boolean,
    val found: Boolean,
    val value: Any? = null,
    val valueType: String? = null
)

object DashboardJsonPathValidator {
    private const val MAX_PATH_LENGTH = 300

    private sealed interface PathToken {
        data class Property(val name: String) : PathToken
        data class Index(val value: Int) : PathToken
    }

    fun canonicalize(path: String): String? {
        val trimmed = path.trim()
        if (trimmed.isBlank()) return null
        val rooted = when {
            trimmed.startsWith('$') -> trimmed
            trimmed.startsWith('.') || trimmed.startsWith('[') -> "\$$trimmed"
            else -> "\$.$trimmed"
        }
        return rooted.takeIf { parse(it) != null }
    }

    fun resolve(root: Any?, path: String): JsonPathResolution {
        val canonicalPath = canonicalize(path)
            ?: return JsonPathResolution(pathSafe = false, found = false)
        val tokens = parse(canonicalPath)
            ?: return JsonPathResolution(pathSafe = false, found = false)
        var current: Any? = root
        for (token in tokens) {
            val next = when (token) {
                is PathToken.Property -> readProperty(current, token.name)
                is PathToken.Index -> readIndex(current, token.value)
            }
            if (!next.first) return JsonPathResolution(pathSafe = true, found = false)
            current = next.second
        }
        val normalized = if (current === JSONObject.NULL) null else current
        return JsonPathResolution(
            pathSafe = true,
            found = true,
            value = normalized,
            valueType = valueTypeOf(normalized)
        )
    }

    fun valueTypeOf(value: Any?): String {
        return when (value) {
            null, JSONObject.NULL -> "null"
            is Number -> "number"
            is String -> "string"
            is Boolean -> "boolean"
            is JSONObject, is Map<*, *> -> "object"
            is JSONArray, is List<*>, is Array<*> -> "array"
            else -> "unknown"
        }
    }

    fun sampleValue(value: Any?): Any {
        return when (value) {
            null, JSONObject.NULL -> "null"
            is Number, is Boolean -> value
            is String -> value.take(200)
            is JSONObject, is JSONArray -> value.toString().take(500)
            is Map<*, *>, is List<*>, is Array<*> -> value.toString().take(500)
            else -> value.toString().take(200)
        }
    }

    private fun readProperty(current: Any?, name: String): Pair<Boolean, Any?> {
        return when (current) {
            is JSONObject -> if (current.has(name)) true to current.opt(name) else false to null
            is Map<*, *> -> if (current.containsKey(name)) true to current[name] else false to null
            else -> false to null
        }
    }

    private fun readIndex(current: Any?, index: Int): Pair<Boolean, Any?> {
        if (index < 0) return false to null
        return when (current) {
            is JSONArray -> if (index < current.length()) true to current.opt(index) else false to null
            is List<*> -> if (index < current.size) true to current[index] else false to null
            is Array<*> -> if (index < current.size) true to current[index] else false to null
            else -> false to null
        }
    }

    private fun parse(path: String): List<PathToken>? {
        if (path.isBlank() || path.length > MAX_PATH_LENGTH || path.first() != '$') return null
        val tokens = mutableListOf<PathToken>()
        var index = 1
        while (index < path.length) {
            when (path[index]) {
                '.' -> {
                    index++
                    val start = index
                    while (index < path.length && path[index] != '.' && path[index] != '[') index++
                    if (start == index) return null
                    val name = path.substring(start, index)
                    if (!isSafeProperty(name)) return null
                    tokens += PathToken.Property(name)
                }
                '[' -> {
                    val closing = path.indexOf(']', startIndex = index + 1)
                    if (closing < 0) return null
                    val content = path.substring(index + 1, closing)
                    val numericIndex = content.toIntOrNull()
                    when {
                        numericIndex != null && numericIndex >= 0 -> tokens += PathToken.Index(numericIndex)
                        content.length >= 2 &&
                            ((content.first() == '\'' && content.last() == '\'') ||
                                (content.first() == '"' && content.last() == '"')) -> {
                            val name = content.substring(1, content.length - 1)
                            if (!isSafeProperty(name)) return null
                            tokens += PathToken.Property(name)
                        }
                        else -> return null
                    }
                    index = closing + 1
                }
                else -> return null
            }
        }
        return tokens
    }

    private fun isSafeProperty(name: String): Boolean {
        if (name.isBlank() || name.length > 100) return false
        return name.none { it in charArrayOf('*', '?', '(', ')', '@', '\\', '\n', '\r') }
    }
}
