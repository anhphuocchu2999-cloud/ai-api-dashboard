package com.java.myapplication

import com.java.myapplication.discovery.DashboardDiscoveryRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardDiscoveryRulesTest {
    @Test
    fun onlyAcceptsCredentialFreeHttpsUrls() {
        assertTrue(DashboardDiscoveryRules.isHttpsUrl("https://dashboard.example.com/usage"))
        assertFalse(DashboardDiscoveryRules.isHttpsUrl("http://dashboard.example.com/usage"))
        assertFalse(DashboardDiscoveryRules.isHttpsUrl("https://user:pass@dashboard.example.com/usage"))
        assertFalse(DashboardDiscoveryRules.isHttpsUrl("javascript:alert(1)"))
    }

    @Test
    fun normalizesOpenAiCompatibleEndpointWithoutQuery() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            DashboardDiscoveryRules.normalizeChatCompletionsUrl("https://api.example.com/v1?secret=no")
        )
        assertEquals(
            "https://api.example.com/openai/v1/chat/completions",
            DashboardDiscoveryRules.normalizeChatCompletionsUrl("https://api.example.com/openai/v1/models")
        )
    }

    @Test
    fun removesQueriesAndRedactsCommonSecrets() {
        assertEquals(
            "https://dashboard.example.com/api/usage",
            DashboardDiscoveryRules.withoutQuery("https://dashboard.example.com/api/usage?token=secret#part")
        )
        val redacted = DashboardDiscoveryRules.redactFreeText(
            "Bearer abcdefghijklmnop user@example.com sk-abcdefghijklmnopqrstuvwxyz"
        )
        assertFalse(redacted.contains("abcdefghijklmnop"))
        assertFalse(redacted.contains("user@example.com"))
        assertFalse(redacted.contains("sk-abcdefghijklmnopqrstuvwxyz"))
        assertTrue(DashboardDiscoveryRules.hasQueryOrFragment("https://dashboard.example.com/api/usage?range=month"))
        assertTrue(DashboardDiscoveryRules.hasQueryOrFragment("https://dashboard.example.com/api/usage#part"))
        assertFalse(DashboardDiscoveryRules.hasQueryOrFragment("https://dashboard.example.com/api/usage"))
    }

    @Test
    fun preservesUsageTokenFieldsButRedactsCredentials() {
        assertFalse(DashboardDiscoveryRules.isSensitiveFieldName("input_tokens"))
        assertFalse(DashboardDiscoveryRules.isSensitiveFieldName("remaining_tokens"))
        assertTrue(DashboardDiscoveryRules.isSensitiveFieldName("access_token"))
        assertTrue(DashboardDiscoveryRules.isSensitiveFieldName("authorization"))
        assertTrue(DashboardDiscoveryRules.isSensitiveFieldName("csrf_token"))
        assertTrue(DashboardDiscoveryRules.shouldRedactStringValue("token", "opaque-secret-value"))
        assertFalse(DashboardDiscoveryRules.shouldRedactStringValue("tokens", "1200"))
    }
}
