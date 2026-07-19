package com.java.myapplication

import com.java.myapplication.discovery.DashboardReplayHeaderPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardReplayHeaderPolicyTest {
    @Test
    fun keepsOnlyBoundedAuthenticationHeaders() {
        val headers = DashboardReplayHeaderPolicy.sanitize(
            linkedMapOf(
                "Authorization" to "Bearer local-secret",
                "X-CSRF-Token" to "csrf-local-secret",
                "New-API-User" to "42",
                "Cookie" to "must-not-be-copied-here",
                "Origin" to "https://attacker.example",
                "X-Unknown" to "ignored",
                "X-API-Key" to "bad\r\nInjected: value"
            )
        )

        assertEquals("Bearer local-secret", headers["authorization"])
        assertEquals("csrf-local-secret", headers["x-csrf-token"])
        assertEquals("42", headers["new-api-user"])
        assertFalse(headers.containsKey("cookie"))
        assertFalse(headers.containsKey("origin"))
        assertFalse(headers.containsKey("x-unknown"))
        assertFalse(headers.containsKey("x-api-key"))
    }

    @Test
    fun requestKeyAcceptsSameOriginGetQueryAndPostWithoutLeakingAcrossOrigins() {
        val origin = "https://dashboard.example.com"
        assertEquals(
            "GET https://dashboard.example.com/api/usage",
            DashboardReplayHeaderPolicy.requestKey(
                "GET",
                "https://dashboard.example.com/api/usage",
                origin
            )
        )
        assertEquals(
            "POST https://dashboard.example.com/api/usage",
            DashboardReplayHeaderPolicy.requestKey(
                "POST",
                "https://dashboard.example.com/api/usage",
                origin
            )
        )
        assertEquals(
            "GET https://dashboard.example.com/api/usage?month=current",
            DashboardReplayHeaderPolicy.requestKey(
                "GET",
                "https://dashboard.example.com/api/usage?month=current",
                origin
            )
        )
        assertNull(
            DashboardReplayHeaderPolicy.requestKey(
                "GET",
                "https://other.example/api/usage",
                origin
            )
        )
        assertNull(
            DashboardReplayHeaderPolicy.requestKey(
                "PUT",
                "https://dashboard.example.com/api/usage",
                origin
            )
        )
        assertTrue(DashboardReplayHeaderPolicy.sanitize(emptyMap()).isEmpty())
    }
}
