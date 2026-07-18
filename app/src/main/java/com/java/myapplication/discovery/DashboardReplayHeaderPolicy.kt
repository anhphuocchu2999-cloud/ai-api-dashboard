package com.java.myapplication.discovery

/**
 * Restricts which WebView request headers may be replayed outside the browser.
 *
 * Values accepted here are credentials. Callers must keep them in memory or Android-Keystore
 * encrypted storage and must never add them to the AI prompt or ordinary logs.
 */
object DashboardReplayHeaderPolicy {
    private const val MAX_HEADER_COUNT = 12
    private const val MAX_VALUE_LENGTH = 8_192

    private val allowedNames = listOf(
        "authorization",
        "x-api-key",
        "api-key",
        "x-auth-token",
        "auth-token",
        "x-access-token",
        "access-token",
        "x-token",
        "token",
        "x-csrf-token",
        "x-xsrf-token",
        "csrf-token",
        "xsrf-token",
        "new-api-user",
        "x-user-id",
        "user-id",
        "x-tenant-id",
        "x-project-id",
        "x-organization-id",
        "x-requested-with"
    )

    fun sanitize(headers: Map<String, String>): Map<String, String> {
        val output = linkedMapOf<String, String>()
        val normalized = headers.entries.associate { (name, value) ->
            name.trim().lowercase() to value
        }
        for (name in allowedNames) {
            val value = normalized[name].orEmpty().trim()
            if (value.isBlank() || value.length > MAX_VALUE_LENGTH) continue
            if (value.any { it == '\r' || it == '\n' || it == '\u0000' }) continue
            output[name] = value
            if (output.size >= MAX_HEADER_COUNT) break
        }
        return output
    }

    fun requestKey(method: String, url: String, lockedOrigin: String): String? {
        if (!method.equals("GET", ignoreCase = true)) return null
        if (DashboardDiscoveryRules.originOf(url) != lockedOrigin) return null
        if (DashboardDiscoveryRules.hasQueryOrFragment(url, lockedOrigin)) return null
        val endpoint = DashboardDiscoveryRules.withoutQuery(url, lockedOrigin)
        if (!DashboardDiscoveryRules.isHttpsUrl(endpoint)) return null
        return "GET $endpoint"
    }
}
