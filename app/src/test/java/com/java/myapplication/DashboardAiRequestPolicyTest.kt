package com.java.myapplication

import com.java.myapplication.discovery.DashboardAiRequestPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardAiRequestPolicyTest {
    @Test
    fun promptOnlyUsesHighestPriorityEightCandidates() {
        val candidates = (1..12).toList()

        assertEquals((1..8).toList(), DashboardAiRequestPolicy.selectPromptCandidates(candidates))
    }

    @Test
    fun promptTextLimitsAreDeterministic() {
        val body = "x".repeat(5_000)
        val visibleText = "y".repeat(5_000)

        assertEquals(4_000, DashboardAiRequestPolicy.limitBody(body).length)
        assertEquals(4_000, DashboardAiRequestPolicy.limitVisibleText(visibleText).length)
    }

    @Test
    fun timeoutRemainsFiniteWithoutAutomaticRetry() {
        assertEquals(20_000, DashboardAiRequestPolicy.CONNECT_TIMEOUT_MS)
        assertEquals(120_000, DashboardAiRequestPolicy.READ_TIMEOUT_MS)
    }
}
