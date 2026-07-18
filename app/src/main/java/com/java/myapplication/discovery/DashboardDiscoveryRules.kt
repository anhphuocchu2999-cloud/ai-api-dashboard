package com.java.myapplication.discovery

import java.net.URI

object DashboardDiscoveryRules {
    private val bearerPattern = Regex("(?i)\\bBearer\\s+[A-Za-z0-9._~+/-]{8,}")
    private val jwtPattern = Regex("\\b[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b")
    private val apiKeyPattern = Regex("(?i)\\b(?:sk|ghp|github_pat|AIza)[-_A-Za-z0-9]{12,}\\b")
    private val emailPattern = Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b")
    private val phonePattern = Regex("(?<!\\d)(?:\\+?\\d[\\d ()-]{8,}\\d)(?!\\d)")
    private val labelledSecretPattern = Regex("(?i)(api[_ -]?key|access[_ -]?token|refresh[_ -]?token|secret)\\s*[:=]\\s*\\S{8,}")
    private val opaqueSecretPattern = Regex("^[A-Za-z0-9_~+/-]{28,}$")

    fun isHttpsUrl(value: String): Boolean {
        return parseHttps(value) != null
    }

    fun originOf(value: String): String? {
        val uri = parseHttps(value) ?: return null
        val port = uri.port
        val suffix = if (port != -1 && port != 443) ":$port" else ""
        return "https://${uri.host.lowercase()}$suffix"
    }

    fun withoutQuery(value: String, baseOrigin: String? = null): String {
        return try {
            val raw = URI(value)
            val resolved = if (!raw.isAbsolute && baseOrigin != null) URI(baseOrigin).resolve(raw) else raw
            URI(resolved.scheme, resolved.authority, resolved.path.ifBlank { "/" }, null, null).toString()
        } catch (_: Exception) {
            value.substringBefore('?').substringBefore('#').take(500)
        }
    }

    fun hasQueryOrFragment(value: String, baseOrigin: String? = null): Boolean {
        return try {
            val raw = URI(value)
            val resolved = if (!raw.isAbsolute && baseOrigin != null) URI(baseOrigin).resolve(raw) else raw
            !resolved.rawQuery.isNullOrBlank() || !resolved.rawFragment.isNullOrBlank()
        } catch (_: Exception) {
            true
        }
    }

    fun normalizeChatCompletionsUrl(apiBase: String): String? {
        val uri = parseHttps(apiBase) ?: return null
        var path = uri.path.orEmpty().trimEnd('/')
        path = when {
            path.endsWith("/chat/completions") -> path
            path.endsWith("/v1/models") -> path.removeSuffix("/models") + "/chat/completions"
            path.endsWith("/models") -> path.removeSuffix("/models") + "/chat/completions"
            path.endsWith("/v1") -> "$path/chat/completions"
            else -> "$path/v1/chat/completions"
        }
        return URI("https", uri.userInfo, uri.host, uri.port, path, null, null).toString()
    }

    fun isSensitiveFieldName(name: String): Boolean {
        val normalized = name.lowercase().replace('-', '_')
        return normalized in setOf(
            "authorization", "cookie", "set_cookie", "password", "passwd",
            "api_key", "apikey", "access_token", "refresh_token", "auth_token",
            "id_token", "csrf", "csrf_token", "xsrf_token", "credential", "credentials",
            "auth", "auth_key", "private_key", "session", "session_id", "sessionid", "client_secret", "secret",
            "email", "phone", "mobile", "username", "user_name", "account_id", "user_id"
        )
    }

    fun redactFreeText(value: String): String {
        return value
            .replace(bearerPattern, "Bearer [已脱敏]")
            .replace(jwtPattern, "[JWT 已脱敏]")
            .replace(apiKeyPattern, "[API Key 已脱敏]")
            .replace(emailPattern, "[邮箱已脱敏]")
            .replace(phonePattern, "[号码已脱敏]")
            .replace(labelledSecretPattern) { "${it.groupValues[1]}=[已脱敏]" }
    }

    fun shouldRedactStringValue(fieldName: String?, value: String): Boolean {
        val normalized = fieldName.orEmpty().lowercase().replace('-', '_')
        return (normalized == "token" && value.length >= 8) || opaqueSecretPattern.matches(value)
    }

    private fun parseHttps(value: String): URI? {
        return try {
            val uri = URI(value.trim())
            if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank() || uri.userInfo != null) {
                null
            } else {
                uri
            }
        } catch (_: Exception) {
            null
        }
    }
}
