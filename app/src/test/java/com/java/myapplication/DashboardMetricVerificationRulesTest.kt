package com.java.myapplication

import com.java.myapplication.discovery.DashboardMetricVerificationRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardMetricVerificationRulesTest {
    private val captured = setOf("https://openrouter.fans/dashboard/billing/usage")

    @Test
    fun pageOnlyBalanceIsNotTreatedAsRefreshable() {
        val reason = DashboardMetricVerificationRules.precheck(
            metricType = "balance",
            endpoint = "",
            jsonPath = "",
            claimedType = "number",
            capturedEndpoints = captured
        )
        assertEquals("只从页面文字识别，尚未找到真实数据接口", reason)
    }

    @Test
    fun capturedTotalUsageCanContinueToLocalValueCheck() {
        val reason = DashboardMetricVerificationRules.precheck(
            metricType = "usage",
            endpoint = "https://openrouter.fans/dashboard/billing/usage",
            jsonPath = "$.total_usage",
            claimedType = "number",
            capturedEndpoints = captured
        )
        assertNull(reason)
    }

    @Test
    fun inventedEndpointIsRejected() {
        val reason = DashboardMetricVerificationRules.precheck(
            metricType = "usage",
            endpoint = "https://attacker.example/usage",
            jsonPath = "$.total_usage",
            claimedType = "number",
            capturedEndpoints = captured
        )
        assertEquals("模型给出的接口不属于本次真实捕获", reason)
    }
}
