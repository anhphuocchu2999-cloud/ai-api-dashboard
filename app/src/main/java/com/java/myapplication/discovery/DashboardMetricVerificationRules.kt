package com.java.myapplication.discovery

object DashboardMetricVerificationRules {
    private val allowedMetricTypes = setOf(
        "balance", "usage", "quota", "tokens", "requests",
        "subscription", "reset_time", "other"
    )
    private val allowedValueTypes = setOf("number", "string", "boolean", "object", "array")

    fun precheck(
        metricType: String,
        endpoint: String,
        jsonPath: String,
        claimedType: String,
        capturedEndpoints: Set<String>
    ): String? {
        return when {
            metricType !in allowedMetricTypes -> "指标类型不在安全范围内"
            endpoint.isBlank() || jsonPath.isBlank() -> "只从页面文字识别，尚未找到真实数据接口"
            endpoint !in capturedEndpoints -> "模型给出的接口不属于本次真实捕获"
            claimedType !in allowedValueTypes -> "模型没有给出可核对的数据类型"
            else -> null
        }
    }
}
