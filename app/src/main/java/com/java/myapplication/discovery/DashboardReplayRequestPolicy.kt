package com.java.myapplication.discovery

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URI
import java.net.URLDecoder

data class DashboardCapturedRequestBody(
    val kind: String,
    val rawBody: String,
    val fieldNames: List<String>,
    val error: String? = null
)

/** Pure security rules for request URLs and replayable GET/POST bodies. */
object DashboardReplayRequestPolicy {
    const val BODY_NONE = "none"
    const val BODY_JSON = "json"
    const val BODY_FORM = "form"

    private const val MAX_REQUEST_URL_CHARS = 4_096
    private const val MAX_REQUEST_BODY_CHARS = 16_384
    private const val MAX_FIELD_COUNT = 40
    private const val MAX_FIELD_NAME_CHARS = 128

    fun normalizeRequestUrl(method: String, url: String, lockedOrigin: String): String? {
        if (method.uppercase() !in setOf("GET", "POST")) return null
        val resolved = runCatching {
            val raw = URI(url.trim())
            if (raw.isAbsolute) raw else URI(lockedOrigin).resolve(raw)
        }.getOrNull() ?: return null
        val normalized = resolved.toASCIIString().substringBefore('#')
        if (normalized.length > MAX_REQUEST_URL_CHARS) return null
        if (!DashboardDiscoveryRules.isHttpsUrl(normalized)) return null
        if (DashboardDiscoveryRules.originOf(normalized) != lockedOrigin) return null
        return normalized
    }

    fun queryParameterNames(requestUrl: String): List<String>? {
        val rawQuery = runCatching { URI(requestUrl).rawQuery }.getOrNull() ?: return emptyList()
        if (rawQuery.isBlank()) return emptyList()
        val parts = rawQuery.split('&')
        if (parts.size > MAX_FIELD_COUNT) return null
        val names = mutableListOf<String>()
        for (part in parts) {
            val rawName = part.substringBefore('=')
            val name = runCatching { URLDecoder.decode(rawName, Charsets.UTF_8.name()) }.getOrNull()
                ?.trim()
                ?: return null
            if (!safeFieldName(name)) return null
            if (name !in names) names += name
        }
        return names
    }

    fun classifyBody(
        method: String,
        contentType: String,
        rawBody: String,
        bodyTruncated: Boolean,
        captureError: String?
    ): DashboardCapturedRequestBody {
        if (method.equals("GET", ignoreCase = true)) {
            return if (rawBody.isBlank() && captureError.isNullOrBlank()) {
                DashboardCapturedRequestBody(BODY_NONE, "", emptyList())
            } else {
                blocked("GET 请求携带了不能安全重放的请求体")
            }
        }
        if (!method.equals("POST", ignoreCase = true)) {
            return blocked("该接口使用 $method；需要网页登录辅助刷新")
        }
        if (!captureError.isNullOrBlank()) return blocked(captureError)
        if (bodyTruncated || rawBody.length > MAX_REQUEST_BODY_CHARS) {
            return blocked("POST 请求体超过 16KB；需要网页登录辅助刷新")
        }
        if (rawBody.isBlank()) return blocked("POST 请求体为空或未能捕获；需要网页登录辅助刷新")

        val mediaType = contentType.substringBefore(';').trim().lowercase()
        val trimmed = rawBody.trim()
        if (mediaType == "application/json" || mediaType.endsWith("+json") || trimmed.startsWith('{') || trimmed.startsWith('[')) {
            val parsed = runCatching { JSONTokener(rawBody).nextValue() }.getOrNull()
            if (parsed !is JSONObject && parsed !is JSONArray) {
                return blocked("POST JSON 请求体格式无效；需要重新捕获")
            }
            val fieldNames = if (parsed is JSONObject) {
                parsed.keys().asSequence().toList().also { names ->
                    if (names.size > MAX_FIELD_COUNT || names.any { !safeFieldName(it) }) {
                        return blocked("POST JSON 字段过多或字段名无效；需要网页登录辅助刷新")
                    }
                }
            } else {
                emptyList()
            }
            return DashboardCapturedRequestBody(BODY_JSON, rawBody, fieldNames)
        }

        if (mediaType == "application/x-www-form-urlencoded") {
            val parts = rawBody.split('&')
            if (parts.size > MAX_FIELD_COUNT) return blocked("POST Form 字段过多；需要网页登录辅助刷新")
            val names = mutableListOf<String>()
            for (part in parts) {
                val rawName = part.substringBefore('=')
                val name = runCatching { URLDecoder.decode(rawName, Charsets.UTF_8.name()) }.getOrNull()
                    ?.trim()
                    ?: return blocked("POST Form 字段编码无效；需要重新捕获")
                if (!safeFieldName(name)) return blocked("POST Form 字段名无效；需要网页登录辅助刷新")
                if (name !in names) names += name
            }
            return DashboardCapturedRequestBody(BODY_FORM, rawBody, names)
        }

        return blocked("POST 请求体不是有限 JSON 或 Form；需要网页登录辅助刷新")
    }

    private fun safeFieldName(name: String): Boolean =
        name.isNotBlank() &&
            name.length <= MAX_FIELD_NAME_CHARS &&
            name.none { it == '\r' || it == '\n' || it == '\u0000' }

    private fun blocked(message: String) = DashboardCapturedRequestBody(
        kind = BODY_NONE,
        rawBody = "",
        fieldNames = emptyList(),
        error = message
    )
}
