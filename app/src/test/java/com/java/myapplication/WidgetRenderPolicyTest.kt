package com.java.myapplication

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRenderPolicyTest {
    @Test
    fun unconfiguredCardNeverShowsPlayfulNetworkStatus() {
        assertFalse(
            WidgetRenderPolicy.showPlayfulStatus(
                isConfigured = false,
                isFallback = false,
                isSuccess = false,
                isAvailable = false
            )
        )
    }

    @Test
    fun configuredCardUsesTheSameStatusForLoadingFallbackAndFailure() {
        assertTrue(WidgetRenderPolicy.showPlayfulStatus(true, true, true, true))
        assertTrue(WidgetRenderPolicy.showPlayfulStatus(true, false, false, false))
        assertFalse(WidgetRenderPolicy.showPlayfulStatus(true, false, true, true))
    }
}
