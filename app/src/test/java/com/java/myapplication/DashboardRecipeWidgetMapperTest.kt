package com.java.myapplication

import com.java.myapplication.adapter.DashboardRecipeWidgetMapper
import com.java.myapplication.discovery.DashboardReplayMetric
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardRecipeWidgetMapperTest {
    @Test
    fun prioritizesBalanceAndKeepsUsageSeparateFromAuxiliaryFields() {
        val projection = DashboardRecipeWidgetMapper.project(
            listOf(
                metric("requests", "请求次数", "18", "次"),
                metric("other", "账号等级", "Pro", ""),
                metric("balance", "可用余额", "12.5", "USD"),
                metric("tokens", "Token 用量", "900", "token")
            )
        )

        assertEquals("可用余额", projection.primary.label)
        assertEquals("\$12.5", projection.primary.value)
        assertEquals(listOf("请求次数", "Token 用量"), projection.usage.map { it.label })
        assertEquals(listOf("账号等级"), projection.auxiliary.map { it.label })
    }

    @Test
    fun doesNotInventPercentageFromOrdinaryQuotaValue() {
        val projection = DashboardRecipeWidgetMapper.project(
            listOf(metric("quota", "剩余额度", "5050", "token"))
        )

        assertEquals("剩余额度", projection.primary.label)
        assertEquals("5050 token", projection.primary.value)
        assertEquals(0, projection.usage.size)
        assertEquals(0, projection.auxiliary.size)
    }

    private fun metric(type: String, label: String, value: String, unit: String) =
        DashboardReplayMetric(type, label, value, unit)
}
