package com.java.myapplication

import com.java.myapplication.discovery.CapturedResponseCandidate
import com.java.myapplication.discovery.DashboardMetricRecipe
import com.java.myapplication.discovery.DashboardRecipeRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardRecipeRulesTest {
    private val endpoint = "https://dashboard.example.com/api/v1/usage"
    private val metric = DashboardMetricRecipe(
        type = "usage",
        label = "本月用量",
        endpoint = endpoint,
        jsonPath = "$.data.total",
        valueType = "number",
        unit = "CNY"
    )

    @Test
    fun createsSameOriginQueryFreeGetRecipe() {
        val draft = DashboardRecipeRules.create(
            pagePurpose = "用量仪表盘",
            dashboardUrl = "https://dashboard.example.com/console?tab=usage",
            lockedOrigin = "https://dashboard.example.com",
            metrics = listOf(metric),
            candidates = listOf(candidate(method = "GET", hadQuery = false)),
            now = 1234L
        )

        assertNull(draft.error)
        assertNotNull(draft.recipe)
        val recipe = requireNotNull(draft.recipe)
        assertEquals("https://dashboard.example.com/console", recipe.dashboardUrl)
        assertEquals(listOf(endpoint), recipe.endpoints)
        assertEquals(0L, recipe.lastSuccessAt)
        assertEquals("Bearer local-secret", draft.replayHeadersByEndpoint[endpoint]?.get("authorization"))
    }

    @Test
    fun rejectsPostAndQueryDependentCaptures() {
        val post = DashboardRecipeRules.create(
            "用量", "https://dashboard.example.com/console", "https://dashboard.example.com",
            listOf(metric), listOf(candidate(method = "POST", hadQuery = false)), 1L
        )
        assertTrue(post.error.orEmpty().contains("GET"))

        val query = DashboardRecipeRules.create(
            "用量", "https://dashboard.example.com/console", "https://dashboard.example.com",
            listOf(metric), listOf(candidate(method = "GET", hadQuery = true)), 1L
        )
        assertTrue(query.error.orEmpty().contains("查询参数"))
    }

    @Test
    fun rejectsCrossOriginEndpoint() {
        val draft = DashboardRecipeRules.create(
            "用量", "https://dashboard.example.com/console", "https://dashboard.example.com",
            listOf(metric.copy(endpoint = "https://api.other.example/usage")),
            listOf(candidate(endpoint = "https://api.other.example/usage")), 1L
        )
        assertTrue(draft.error.orEmpty().contains("同站点"))
    }

    @Test
    fun createsPuppyRouterRecipeFromVerifiedUserAndUsageEndpoints() {
        val userEndpoint = "https://puppyrouter.com/api/user/self"
        val dataEndpoint = "https://puppyrouter.com/api/data/self"
        val draft = DashboardRecipeRules.create(
            pagePurpose = "PuppyRouter API 控制台仪表盘",
            dashboardUrl = "https://puppyrouter.com/console",
            lockedOrigin = "https://puppyrouter.com",
            metrics = listOf(
                metric.copy(
                    type = "balance",
                    label = "账户余额",
                    endpoint = userEndpoint,
                    jsonPath = "$.data.quota"
                ),
                metric.copy(
                    type = "tokens",
                    label = "Token 用量",
                    endpoint = dataEndpoint,
                    jsonPath = "$.data[0].token_used",
                    unit = "tokens"
                )
            ),
            candidates = listOf(
                candidate(endpoint = userEndpoint),
                candidate(endpoint = dataEndpoint)
            ),
            now = 2L
        )

        assertNull(draft.error)
        assertEquals(listOf(userEndpoint, dataEndpoint), requireNotNull(draft.recipe).endpoints)
        assertEquals("Bearer local-secret", draft.replayHeadersByEndpoint[userEndpoint]?.get("authorization"))
        assertEquals("Bearer local-secret", draft.replayHeadersByEndpoint[dataEndpoint]?.get("authorization"))
    }

    private fun candidate(
        endpoint: String = this.endpoint,
        method: String = "GET",
        hadQuery: Boolean = false
    ) = CapturedResponseCandidate(
        endpoint = endpoint,
        method = method,
        status = 200,
        sanitizedJson = "{\"data\":{\"total\":12}}",
        hadQuery = hadQuery,
        replayHeaders = mapOf(
            "Authorization" to "Bearer local-secret",
            "Cookie" to "must-not-enter-replay-headers"
        )
    )
}
