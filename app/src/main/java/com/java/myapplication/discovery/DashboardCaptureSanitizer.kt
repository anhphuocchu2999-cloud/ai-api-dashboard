package com.java.myapplication.discovery

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class PreparedDashboardCapture(
    val promptPayload: String,
    val rawCaptureCount: Int,
    val candidateCount: Int,
    val allowedEndpoints: Set<String>
)

object DashboardCaptureSanitizer {
    private const val MAX_CANDIDATES = 12
    private const val MAX_BODY_CHARS = 8_000
    private const val MAX_VISIBLE_TEXT_CHARS = 6_000
    private const val MAX_OBJECT_KEYS = 80
    private const val MAX_ARRAY_ITEMS = 20
    private const val MAX_STRING_CHARS = 600
    private const val MAX_DEPTH = 8

    private val endpointKeywords = listOf(
        "usage", "billing", "balance", "quota", "credit", "subscription",
        "dashboard", "summary", "account", "wallet", "token", "request", "limit", "plan"
    )

    fun prepare(exportedJson: String, lockedOrigin: String): PreparedDashboardCapture {
        val exported = JSONObject(exportedJson)
        val records = exported.optJSONArray("records") ?: JSONArray()
        val candidates = mutableListOf<Pair<Int, JSONObject>>()

        for (index in 0 until records.length()) {
            val record = records.optJSONObject(index) ?: continue
            val rawBody = record.optString("body")
            val parsedBody = parseJson(rawBody) ?: continue
            val endpoint = DashboardDiscoveryRules.withoutQuery(record.optString("url"), lockedOrigin)
            if (!DashboardDiscoveryRules.isHttpsUrl(endpoint)) continue
            val score = scoreCandidate(endpoint, record.optInt("status"))
            val sanitizedBody = sanitizeNode(parsedBody, null, 0).toString().take(MAX_BODY_CHARS)
            candidates += score to JSONObject()
                .put("endpoint", endpoint)
                .put("method", record.optString("method", "GET").uppercase().take(12))
                .put("status", record.optInt("status"))
                .put("sanitizedJson", sanitizedBody)
        }

        val selected = candidates
            .sortedByDescending { it.first }
            .take(MAX_CANDIDATES)
            .map { it.second }

        val visibleText = DashboardDiscoveryRules.redactFreeText(
            exported.optString("visibleText")
                .replace(Regex("\\s+"), " ")
                .take(MAX_VISIBLE_TEXT_CHARS)
        )
        val pageUrl = DashboardDiscoveryRules.withoutQuery(exported.optString("pageUrl"), lockedOrigin)
        val payload = JSONObject()
            .put("source", "untrusted_dashboard_capture")
            .put("pageUrl", pageUrl)
            .put("visibleText", visibleText)
            .put("responses", JSONArray(selected))

        return PreparedDashboardCapture(
            promptPayload = payload.toString(),
            rawCaptureCount = records.length(),
            candidateCount = selected.size,
            allowedEndpoints = selected.map { it.optString("endpoint") }.filter { it.isNotBlank() }.toSet()
        )
    }

    private fun parseJson(raw: String): Any? {
        if (raw.isBlank()) return null
        return try {
            when (val value = JSONTokener(raw).nextValue()) {
                is JSONObject, is JSONArray -> value
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun scoreCandidate(endpoint: String, status: Int): Int {
        val lowered = endpoint.lowercase()
        val keywordScore = endpointKeywords.count { lowered.contains(it) } * 10
        val statusScore = if (status in 200..299) 5 else 0
        return keywordScore + statusScore
    }

    private fun sanitizeNode(value: Any?, fieldName: String?, depth: Int): Any {
        if (fieldName != null && DashboardDiscoveryRules.isSensitiveFieldName(fieldName)) {
            return "[已脱敏]"
        }
        if (depth >= MAX_DEPTH) return "[结构过深，已截断]"

        return when (value) {
            null, JSONObject.NULL -> JSONObject.NULL
            is JSONObject -> {
                val output = JSONObject()
                val keys = value.keys().asSequence().take(MAX_OBJECT_KEYS).toList()
                keys.forEach { key -> output.put(key, sanitizeNode(value.opt(key), key, depth + 1)) }
                if (value.length() > keys.size) output.put("_truncated", true)
                output
            }
            is JSONArray -> {
                val output = JSONArray()
                val count = minOf(value.length(), MAX_ARRAY_ITEMS)
                for (index in 0 until count) output.put(sanitizeNode(value.opt(index), fieldName, depth + 1))
                if (value.length() > count) output.put("[其余数组项已截断]")
                output
            }
            is String -> if (DashboardDiscoveryRules.shouldRedactStringValue(fieldName, value)) {
                "[已脱敏]"
            } else {
                DashboardDiscoveryRules.redactFreeText(value).take(MAX_STRING_CHARS)
            }
            is Number, is Boolean -> value
            else -> DashboardDiscoveryRules.redactFreeText(value.toString()).take(MAX_STRING_CHARS)
        }
    }
}
