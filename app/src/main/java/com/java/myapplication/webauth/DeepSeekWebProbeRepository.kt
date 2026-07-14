package com.java.myapplication.webauth

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * DeepSeek 网页后台只读摸排结果。
 *
 * 仅保存：请求方法、去掉 query/fragment 的 endpoint、HTTP 状态码、JSON 字段路径和类型。
 * 不保存响应值、请求头、Cookie、Token 或 URL 查询参数。
 */
object DeepSeekWebProbeRepository {
    private const val PREFS_NAME = "deepseek_web_probe"
    private const val MAX_RECORDS = 40
    private const val MAX_FIELDS = 80

    data class Record(
        val endpoint: String,
        val method: String,
        val statusCode: Int?,
        val fields: List<String>,
        val capturedAt: Long
    )

    @Synchronized
    fun clear(context: Context, instanceKey: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(storageKey(instanceKey))
            .apply()
    }

    @Synchronized
    fun recordRequest(
        context: Context,
        instanceKey: String,
        url: String,
        method: String
    ) {
        record(
            context = context,
            instanceKey = instanceKey,
            url = url,
            method = method,
            statusCode = null,
            fields = emptyList()
        )
    }

    @Synchronized
    fun recordResponse(
        context: Context,
        instanceKey: String,
        url: String,
        method: String,
        statusCode: Int,
        body: String
    ) {
        record(
            context = context,
            instanceKey = instanceKey,
            url = url,
            method = method,
            statusCode = statusCode,
            fields = extractSchema(body)
        )
    }

    @Synchronized
    fun load(context: Context, instanceKey: String): List<Record> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(storageKey(instanceKey), null)
            ?: return emptyList()

        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val fieldsArray = item.optJSONArray("fields")
                    val fields = buildList {
                        if (fieldsArray != null) {
                            for (fieldIndex in 0 until fieldsArray.length()) {
                                fieldsArray.optString(fieldIndex)
                                    .takeIf { it.isNotBlank() }
                                    ?.let(::add)
                            }
                        }
                    }
                    add(
                        Record(
                            endpoint = item.optString("endpoint", ""),
                            method = item.optString("method", "GET"),
                            statusCode = if (item.has("statusCode")) item.optInt("statusCode") else null,
                            fields = fields,
                            capturedAt = item.optLong("capturedAt", 0L)
                        )
                    )
                }
            }.filter { it.endpoint.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun summary(context: Context, instanceKey: String): String {
        val records = load(context, instanceKey)
        if (records.isEmpty()) {
            return "还没有捕获到接口。登录后停留在用量页面，必要时刷新页面一次。"
        }

        return buildString {
            append("已捕获 ${records.size} 个接口；仅展示 endpoint、状态码和字段结构，不保存数据值。\n\n")
            records.forEachIndexed { index, record ->
                append(index + 1)
                append(". ")
                append(record.method)
                append(" ")
                append(record.endpoint)
                record.statusCode?.let {
                    append("  [HTTP ")
                    append(it)
                    append("]")
                }
                append('\n')
                if (record.fields.isNotEmpty()) {
                    append("字段：")
                    append(record.fields.joinToString("、"))
                    append('\n')
                } else {
                    append("字段：尚未读取到 JSON 结构\n")
                }
                append('\n')
            }
        }
    }

    private fun record(
        context: Context,
        instanceKey: String,
        url: String,
        method: String,
        statusCode: Int?,
        fields: List<String>
    ) {
        val endpoint = sanitizeEndpoint(url) ?: return
        if (isStaticAsset(endpoint)) return

        val existing = load(context, instanceKey).toMutableList()
        val normalizedMethod = method.ifBlank { "GET" }.uppercase()
        val index = existing.indexOfFirst {
            it.endpoint == endpoint && it.method == normalizedMethod
        }
        val previous = existing.getOrNull(index)
        val merged = Record(
            endpoint = endpoint,
            method = normalizedMethod,
            statusCode = statusCode ?: previous?.statusCode,
            fields = if (fields.isNotEmpty()) fields.take(MAX_FIELDS) else previous?.fields.orEmpty(),
            capturedAt = System.currentTimeMillis()
        )

        if (index >= 0) {
            existing[index] = merged
        } else {
            existing.add(merged)
        }

        val trimmed = existing
            .sortedByDescending { it.capturedAt }
            .take(MAX_RECORDS)
            .sortedBy { it.capturedAt }

        val array = JSONArray()
        trimmed.forEach { record ->
            array.put(
                JSONObject()
                    .put("endpoint", record.endpoint)
                    .put("method", record.method)
                    .apply {
                        record.statusCode?.let { put("statusCode", it) }
                    }
                    .put("fields", JSONArray(record.fields))
                    .put("capturedAt", record.capturedAt)
            )
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(storageKey(instanceKey), array.toString())
            .apply()
    }

    private fun sanitizeEndpoint(rawUrl: String): String? {
        return try {
            val uri = Uri.parse(rawUrl)
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return null
            val host = uri.host?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
            val path = uri.encodedPath?.takeIf { it.isNotBlank() } ?: "/"
            "$host$path"
        } catch (_: Exception) {
            null
        }
    }

    private fun isStaticAsset(endpoint: String): Boolean {
        val lower = endpoint.lowercase()
        return listOf(
            ".js", ".css", ".png", ".jpg", ".jpeg", ".webp", ".gif", ".svg",
            ".ico", ".woff", ".woff2", ".ttf", ".map"
        ).any(lower::endsWith)
    }

    private fun extractSchema(body: String): List<String> {
        val trimmed = body.trim()
        if (trimmed.isEmpty() || (trimmed.first() != '{' && trimmed.first() != '[')) {
            return emptyList()
        }

        return try {
            val root = JSONTokener(trimmed.take(200_000)).nextValue()
            val result = linkedSetOf<String>()
            collectSchema(root, "$", result, 0)
            result.take(MAX_FIELDS)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun collectSchema(
        value: Any?,
        path: String,
        result: MutableSet<String>,
        depth: Int
    ) {
        if (depth > 6 || result.size >= MAX_FIELDS) return

        when (value) {
            is JSONObject -> {
                val keys = value.keys()
                while (keys.hasNext() && result.size < MAX_FIELDS) {
                    val key = keys.next()
                    val child = value.opt(key)
                    val childPath = "$path.$key"
                    result.add("$childPath:${typeName(child)}")
                    collectSchema(child, childPath, result, depth + 1)
                }
            }

            is JSONArray -> {
                result.add("$path[]:array")
                val sampleCount = minOf(value.length(), 3)
                for (index in 0 until sampleCount) {
                    collectSchema(value.opt(index), "$path[]", result, depth + 1)
                }
            }
        }
    }

    private fun typeName(value: Any?): String {
        return when (value) {
            null, JSONObject.NULL -> "null"
            is JSONObject -> "object"
            is JSONArray -> "array"
            is Boolean -> "boolean"
            is Number -> "number"
            else -> "string"
        }
    }

    private fun storageKey(instanceKey: String): String {
        val safe = instanceKey.lowercase().replace(Regex("[^a-z0-9_-]+"), "-")
        return "probe_${safe.ifBlank { "unknown" }}"
    }
}
