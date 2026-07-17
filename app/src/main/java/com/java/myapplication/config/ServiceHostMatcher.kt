package com.java.myapplication.config

import java.net.URI
import java.util.Locale

/** URL Host 的统一解析与匹配入口，禁止再用整段 URL 的 contains 判断服务类型。 */
object ServiceHostMatcher {
    fun hostOf(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return null
        return try {
            val normalized = if (trimmed.contains("://")) trimmed else "https://$trimmed"
            URI(normalized).host
                ?.trim()
                ?.lowercase(Locale.ROOT)
                ?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    fun matches(rawUrl: String, hostPattern: String): Boolean {
        val host = hostOf(rawUrl) ?: return false
        return hostMatches(host, hostPattern)
    }

    fun matchesAny(rawUrl: String, hostPatterns: Iterable<String>): Boolean {
        val host = hostOf(rawUrl) ?: return false
        return hostPatterns.any { hostMatches(host, it) }
    }

    fun hostMatches(host: String, hostPattern: String): Boolean {
        val normalizedHost = host.trim().trimEnd('.').lowercase(Locale.ROOT)
        val normalizedPattern = hostPattern.trim().trim('.').lowercase(Locale.ROOT)
        if (normalizedHost.isBlank() || normalizedPattern.isBlank()) return false
        return normalizedHost == normalizedPattern || normalizedHost.endsWith(".$normalizedPattern")
    }
}
