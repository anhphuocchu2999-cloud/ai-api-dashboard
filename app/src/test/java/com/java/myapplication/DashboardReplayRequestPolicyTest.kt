package com.java.myapplication

import com.java.myapplication.discovery.DashboardReplayRequestPolicy
import com.java.myapplication.discovery.DashboardReplayRequestSecret
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardReplayRequestPolicyTest {
    private val origin = "https://dashboard.example.com"

    @Test
    fun keepsSameOriginQueryUrlAndExposesOnlyParameterNames() {
        val url = DashboardReplayRequestPolicy.normalizeRequestUrl(
            "GET",
            "$origin/api/usage?month=current&access_token=local-secret",
            origin
        )

        assertNotNull(url)
        assertEquals(listOf("month", "access_token"), DashboardReplayRequestPolicy.queryParameterNames(requireNotNull(url)))
        assertNull(DashboardReplayRequestPolicy.normalizeRequestUrl("GET", "https://other.example/api", origin))
    }

    @Test
    fun acceptsBoundedFormBody() {
        val form = DashboardReplayRequestPolicy.classifyBody(
            "POST",
            "application/x-www-form-urlencoded",
            "range=month&csrf_token=local-secret",
            false,
            null
        )
        assertNull(form.error)
        assertEquals(DashboardReplayRequestPolicy.BODY_FORM, form.kind)
        assertEquals(listOf("range", "csrf_token"), form.fieldNames)
    }

    @Test
    fun rejectsMultipartBinaryAndOversizedBodiesAsBrowserAssisted() {
        val multipart = DashboardReplayRequestPolicy.classifyBody(
            "POST",
            "multipart/form-data",
            "binary",
            false,
            "multipart 或文件表单不能安全直接重放；需要网页登录辅助刷新"
        )
        assertTrue(multipart.error.orEmpty().contains("网页登录辅助刷新"))

        val oversized = DashboardReplayRequestPolicy.classifyBody(
            "POST",
            "application/json",
            "{}",
            true,
            null
        )
        assertTrue(oversized.error.orEmpty().contains("16KB"))
    }

    @Test
    fun replaySecretStringNeverExposesUrlOrBody() {
        val text = DashboardReplayRequestSecret(
            "https://dashboard.example.com/api?token=local-secret",
            "account_id=local-secret"
        ).toString()

        assertTrue(!text.contains("local-secret"))
        assertTrue(text.contains("已脱敏"))
    }
}
