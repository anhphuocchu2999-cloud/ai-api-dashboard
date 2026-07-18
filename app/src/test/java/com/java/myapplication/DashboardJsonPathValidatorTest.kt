package com.java.myapplication

import com.java.myapplication.discovery.DashboardJsonPathValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardJsonPathValidatorTest {
    private val sample = mapOf(
        "total_usage" to 12.5,
        "data" to mapOf(
            "items" to listOf(
                mapOf("quota" to 5050, "enabled" to true)
            ),
            "total.usage" to 9
        )
    )

    @Test
    fun resolvesSimpleAndArrayPaths() {
        val total = DashboardJsonPathValidator.resolve(sample, "$.total_usage")
        assertTrue(total.pathSafe)
        assertTrue(total.found)
        assertEquals(12.5, total.value)
        assertEquals("number", total.valueType)

        val quota = DashboardJsonPathValidator.resolve(sample, "$.data.items[0].quota")
        assertTrue(quota.found)
        assertEquals(5050, quota.value)
    }

    @Test
    fun canonicalizesSafeRelativeDotPath() {
        val relative = "data.items[0].quota"

        assertEquals("$.data.items[0].quota", DashboardJsonPathValidator.canonicalize(relative))
        val result = DashboardJsonPathValidator.resolve(sample, relative)
        assertTrue(result.pathSafe)
        assertTrue(result.found)
        assertEquals(5050, result.value)
    }

    @Test
    fun supportsQuotedPropertyWithoutExecutingExpressions() {
        val result = DashboardJsonPathValidator.resolve(sample, "$.data['total.usage']")
        assertTrue(result.found)
        assertEquals(9, result.value)
    }

    @Test
    fun rejectsRecursiveWildcardFilterAndScriptPaths() {
        listOf(
            "$..quota",
            "$.data.*",
            "$.items[?(@.enabled)]",
            "$.items[0].value()",
            "data.*",
            "items[?(@.enabled)]",
            "items[0].value()"
        ).forEach { path ->
            val result = DashboardJsonPathValidator.resolve(sample, path)
            assertFalse(path, result.pathSafe)
            assertFalse(path, result.found)
        }
    }

    @Test
    fun distinguishesSafeMissingPathFromUnsafePath() {
        val result = DashboardJsonPathValidator.resolve(sample, "$.data.missing")
        assertTrue(result.pathSafe)
        assertFalse(result.found)
    }
}
