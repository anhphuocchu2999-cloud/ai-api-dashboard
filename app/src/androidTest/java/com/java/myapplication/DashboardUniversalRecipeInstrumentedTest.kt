package com.java.myapplication

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.java.myapplication.discovery.CapturedResponseCandidate
import com.java.myapplication.discovery.DashboardMetricRecipe
import com.java.myapplication.discovery.DashboardCaptureSanitizer
import com.java.myapplication.discovery.DashboardRecipeRepository
import com.java.myapplication.discovery.DashboardRecipeRules
import com.java.myapplication.discovery.DashboardRequestRecipe
import com.java.myapplication.discovery.DashboardReplayRequestPolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DashboardUniversalRecipeInstrumentedTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val repository: DashboardRecipeRepository
        get() = DashboardRecipeRepository(context)

    @Before
    fun clearBefore() {
        repository.clear()
    }

    @After
    fun clearAfter() {
        repository.clear()
    }

    @Test
    fun postJsonBodyAndSensitiveQueryStayEncryptedAndRoundTrip() {
        val endpoint = "https://dashboard.example.com/api/usage"
        val requestUrl = "$endpoint?tenant=tenant-value-unique"
        val requestBody = "{\"range\":\"month-value-unique\",\"account_id\":\"account-secret-unique\"}"
        val classified = DashboardReplayRequestPolicy.classifyBody(
            method = "POST",
            contentType = "application/json; charset=utf-8",
            rawBody = requestBody,
            bodyTruncated = false,
            captureError = null
        )
        assertNull(classified.error)
        assertEquals(DashboardReplayRequestPolicy.BODY_JSON, classified.kind)

        val draft = DashboardRecipeRules.create(
            pagePurpose = "测试仪表盘",
            dashboardUrl = "https://dashboard.example.com/console",
            lockedOrigin = "https://dashboard.example.com",
            metrics = listOf(
                DashboardMetricRecipe(
                    type = "usage",
                    label = "本月用量",
                    endpoint = endpoint,
                    jsonPath = "$.data.total",
                    valueType = "number",
                    unit = "CNY"
                )
            ),
            candidates = listOf(
                CapturedResponseCandidate(
                    endpoint = endpoint,
                    method = "POST",
                    status = 200,
                    sanitizedJson = "{\"data\":{\"total\":12}}",
                    hadQuery = true,
                    requestUrl = requestUrl,
                    requestBodyKind = classified.kind,
                    requestBody = classified.rawBody,
                    requestBodyFieldNames = classified.fieldNames
                )
            ),
            now = 123L
        )
        val recipe = requireNotNull(draft.recipe)
        assertFalse(recipe.isWidgetReplayEligible())
        assertFalse(recipe.toJson().toString().contains("tenant-value-unique"))
        assertFalse(recipe.toJson().toString().contains("account-secret-unique"))
        assertTrue(
            repository.save(
                recipe = recipe,
                cookiesByEndpoint = emptyMap(),
                replayHeadersByEndpoint = emptyMap(),
                replayRequestsByEndpoint = draft.replayRequestsByEndpoint
            )
        )

        val rawFile = File(context.noBackupFilesDir, "dashboard_recipe_v1.json").readText()
        assertFalse(rawFile.contains("tenant-value-unique"))
        assertFalse(rawFile.contains("month-value-unique"))
        assertFalse(rawFile.contains("account-secret-unique"))

        val loaded = requireNotNull(repository.load())
        assertEquals(requestUrl, loaded.replayRequestsByEndpoint[endpoint]?.requestUrl)
        assertEquals(requestBody, loaded.replayRequestsByEndpoint[endpoint]?.requestBody)
        assertEquals("POST", loaded.recipe.effectiveRequestSpecs().single().method)
    }

    @Test
    fun aiPromptNeverContainsQueryValuesOrPostBody() {
        val exported = """
            {
              "pageUrl":"https://dashboard.example.com/console",
              "visibleText":"用量仪表盘",
              "records":[{
                "url":"https://dashboard.example.com/api/usage?tenant=tenant-value-unique",
                "method":"POST",
                "status":200,
                "body":"{\"data\":{\"total\":12}}",
                "requestBody":"{\"range\":\"month-value-unique\",\"account_id\":\"account-secret-unique\"}",
                "requestContentType":"application/json",
                "requestBodyTruncated":false,
                "requestCaptureError":""
              }]
            }
        """.trimIndent()

        val capture = DashboardCaptureSanitizer.prepare(
            exportedJson = exported,
            lockedOrigin = "https://dashboard.example.com"
        )

        assertEquals(1, capture.candidateCount)
        assertFalse(capture.promptPayload.contains("tenant-value-unique"))
        assertFalse(capture.promptPayload.contains("month-value-unique"))
        assertFalse(capture.promptPayload.contains("account-secret-unique"))
        assertTrue(capture.promptPayload.contains("https://dashboard.example.com/api/usage"))
        val candidate = capture.candidates.single()
        assertTrue(candidate.requestUrl.contains("tenant-value-unique"))
        assertTrue(candidate.requestBody.contains("account-secret-unique"))
    }

    @Test
    fun versionOneGetRecipeRemainsReadableAndWidgetEligible() {
        val endpoint = "https://dashboard.example.com/api/usage"
        val root = JSONObject()
            .put("version", 1)
            .put("pagePurpose", "旧版仪表盘")
            .put("dashboardUrl", "https://dashboard.example.com/console")
            .put("origin", "https://dashboard.example.com")
            .put("endpoints", JSONArray().put(endpoint))
            .put(
                "metrics",
                JSONArray().put(
                    JSONObject()
                        .put("type", "usage")
                        .put("label", "本月用量")
                        .put("endpoint", endpoint)
                        .put("jsonPath", "$.data.total")
                        .put("valueType", "number")
                        .put("unit", "CNY")
                )
            )
            .put("createdAt", 1L)
            .put("lastSuccessAt", 2L)

        val recipe = requireNotNull(DashboardRequestRecipe.fromJson(root))
        assertEquals("GET", recipe.effectiveRequestSpecs().single().method)
        assertTrue(recipe.isWidgetReplayEligible())
    }
}
