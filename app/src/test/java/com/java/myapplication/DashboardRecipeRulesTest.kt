package com.java.myapplication

import com.java.myapplication.discovery.CapturedResponseCandidate
import com.java.myapplication.discovery.DashboardMetricRecipe
import com.java.myapplication.discovery.DashboardRecipeRules
import com.java.myapplication.discovery.DashboardRequestRecipe
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
    fun serializedRecipeRoundTripsWithoutCredentialOrSampleValue() {
        val recipe = DashboardRequestRecipe(
            pagePurpose = "用量仪表盘",
            dashboardUrl = "https://dashboard.example.com/console",
            origin = "https://dashboard.example.com",
            endpoints = listOf(endpoint),
            metrics = listOf(metric),
            createdAt = 10L,
            lastSuccessAt = 20L
        )
        val json = recipe.toJson()
        val restored = DashboardRequestRecipe.fromJson(json)

        assertNotNull(restored)
        assertEquals(recipe, restored)
        assertTrue(!json.has("cookie") && !json.has("sampleValue") && !json.has("apiKey"))
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
        hadQuery = hadQuery
    )
}
