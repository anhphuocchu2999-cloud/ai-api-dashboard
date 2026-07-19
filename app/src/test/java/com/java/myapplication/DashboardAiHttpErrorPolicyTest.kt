package com.java.myapplication

import com.java.myapplication.discovery.DashboardAiHttpErrorPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardAiHttpErrorPolicyTest {
    @Test
    fun commonServerErrorsExposeExactStatusAndKeepRetryActionable() {
        listOf(500, 502, 503, 504, 520).forEach { status ->
            val message = DashboardAiHttpErrorPolicy.message(status)
            assertTrue(message.contains("HTTP $status"))
            assertTrue(message.contains("捕获已保留"))
            assertTrue(message.contains("重试"))
        }
    }

    @Test
    fun errorMessageNeverContainsProviderResponseBody() {
        val providerBody = "provider-secret-response"
        val message = DashboardAiHttpErrorPolicy.message(502)
        assertFalse(message.contains(providerBody))
    }

    @Test
    fun existingAuthRateLimitAndCompatibilityCategoriesRemainClear() {
        assertTrue(DashboardAiHttpErrorPolicy.message(401).contains("API Key"))
        assertTrue(DashboardAiHttpErrorPolicy.message(404).contains("API Base"))
        assertTrue(DashboardAiHttpErrorPolicy.message(429).contains("请求过于频繁"))
    }
}
